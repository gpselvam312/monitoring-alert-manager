package com.dxc.monitoring.service.executor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;

@Component
public class ScriptExecutor implements MonitoringExecutor
{
    private final MonitoringResultNormalizer resultNormalizer;
    private final ScriptSecurityValidator scriptSecurityValidator;
    private final int maxOutputBytes;
    private final ExecutorService outputReaderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ScriptExecutor(MonitoringResultNormalizer resultNormalizer, ScriptSecurityValidator scriptSecurityValidator,
            @Value("${monitoring.security.script.max-output-bytes:262144}") int maxOutputBytes)
    {
        this.resultNormalizer = resultNormalizer;
        this.scriptSecurityValidator = scriptSecurityValidator;
        this.maxOutputBytes = Math.max(1024, maxOutputBytes);
    }

    @Override
    public MonitoringJob.MonitorType getType()
    {
        return MonitoringJob.MonitorType.SCRIPT;
    }

    @Override
    public MonitoringExecutionResult execute(MonitoringJob job)
    {
        MonitoringExecutionResult result = new MonitoringExecutionResult();
        result.setResultType(MonitoringResult.ResultType.TEXT);
        OffsetDateTime startedAt = OffsetDateTime.now();
        Process process = null;

        try
        {
            ProcessBuilder builder = new ProcessBuilder(buildCommand(job));
            if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
                builder.directory(new File(job.getWorkingDirectory()));

            process = builder.start();
            Process runningProcess = process;

            // Drain stdout and stderr independently so scripts can emit machine-readable JSON
            // on stdout while keeping diagnostics on stderr.
            CompletableFuture<ProcessOutputCapture.CapturedOutput> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> capture(runningProcess.getInputStream()), outputReaderExecutor);
            CompletableFuture<ProcessOutputCapture.CapturedOutput> stderrFuture =
                    CompletableFuture.supplyAsync(() -> capture(runningProcess.getErrorStream()), outputReaderExecutor);

            int timeoutSeconds = job.getTimeoutSeconds() == null ? 30 : Math.max(1, job.getTimeoutSeconds());
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed)
            {
                terminateProcessTree(process);
                result.setExecutionStatus(MonitoringExecution.ExecutionStatus.TIMEOUT);
                result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
                result.setMessage("Script execution timed out after " + timeoutSeconds + " seconds.");
                result.setErrorMessage("Script execution timed out.");
                String stdout = readOutput(stdoutFuture);
                String stderr = readOutput(stderrFuture);
                result.setRawOutput(combineOutput(stdout, stderr));
                normalizeResult(result, stdout, startedAt, job.getResultParserConfig());
                return result;
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS).text();
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS).text();
            String combinedOutput = combineOutput(stdout, stderr);
            result.setRawOutput(combinedOutput);
            int exitCode = process.exitValue();

            String expected = job.getExpectedResult();
            boolean expectedMatches = expected == null || expected.isBlank() || combinedOutput.contains(expected);
            boolean success = exitCode == 0 && expectedMatches;

            result.setExecutionStatus(success ? MonitoringExecution.ExecutionStatus.SUCCESS
                    : MonitoringExecution.ExecutionStatus.FAILED);
            result.setResultStatus(success ? MonitoringResult.ResultStatus.OK
                    : MonitoringResult.ResultStatus.FAILED);

            if (exitCode != 0)
            {
                result.setMessage("Script failed with exit code " + exitCode + ".");
                result.setErrorMessage("Script exited with code " + exitCode + ".");
            }
            else if (!expectedMatches)
            {
                result.setMessage("Script completed, but the expected result text was not found.");
                result.setErrorMessage(result.getMessage());
            }
            else
            {
                result.setMessage("Script completed successfully.");
            }

            normalizeResult(result, stdout, startedAt, job.getResultParserConfig());
        }
        catch (IllegalArgumentException exception)
        {
            if (process != null)
                terminateProcessTree(process);
            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
            result.setMessage(safeSecurityMessage(exception.getMessage()));
            result.setErrorMessage(safeSecurityMessage(exception.getMessage()));
            normalizeResult(result, exception.getMessage(), startedAt, job.getResultParserConfig());
        }
        catch (InterruptedException exception)
        {
            if (process != null)
                terminateProcessTree(process);
            Thread.currentThread().interrupt();
            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
            result.setMessage("Script execution was interrupted.");
            result.setErrorMessage(exception.getMessage());
            normalizeResult(result, exception.getMessage(), startedAt, job.getResultParserConfig());
        }
        catch (IOException | ExecutionException | TimeoutException exception)
        {
            if (process != null)
                terminateProcessTree(process);
            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
            result.setMessage("Unable to execute or collect output from the monitoring script.");
            result.setErrorMessage(exception.getMessage());
            normalizeResult(result, exception.getMessage(), startedAt, job.getResultParserConfig());
        }
        return result;
    }

    @PreDestroy
    public void shutdownOutputReaders()
    {
        outputReaderExecutor.shutdownNow();
    }

    private ProcessOutputCapture.CapturedOutput capture(InputStream input)
    {
        try
        {
            return ProcessOutputCapture.capture(input, maxOutputBytes);
        }
        catch (IOException exception)
        {
            throw new java.util.concurrent.CompletionException(exception);
        }
    }

    private void terminateProcessTree(Process process)
    {
        List<ProcessHandle> descendants = process.descendants().toList();
        for (int index = descendants.size() - 1; index >= 0; index--)
            descendants.get(index).destroyForcibly();
        process.destroyForcibly();
        try
        {
            process.waitFor(2, TimeUnit.SECONDS);
            for (ProcessHandle descendant : descendants)
                descendant.onExit().get(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException | TimeoutException ignored)
        {
            // Do not block execution cleanup indefinitely after forceful termination.
        }
    }

    private String safeSecurityMessage(String message)
    {
        if (message == null || message.isBlank())
            return "Execution blocked: Script security validation failed. Check execution details for more information.";
        // Validator messages are deliberately authored without exposing local file paths or script contents.
        return message;
    }

    private void normalizeResult(MonitoringExecutionResult result, String output, OffsetDateTime startedAt, String parserConfigJson)
    {
        MonitoringResultNormalizer.NormalizedResult normalized = resultNormalizer.normalize(
                output, result.getExecutionStatus(), result.getResultStatus(), result.getMessage(),
                startedAt, OffsetDateTime.now(), parserConfigJson);
        result.setResultData(normalized.json());
        result.setResultType(normalized.resultType());
        result.setResultStatus(normalized.resultStatus());
        result.setMessage(normalized.message());
    }

    private String combineOutput(String stdout, String stderr)
    {
        if (stderr == null || stderr.isBlank())
        {
            return stdout == null ? "" : stdout;
        }
        if (stdout == null || stdout.isBlank())
        {
            return "[stderr]\n" + stderr;
        }
        return stdout + "\n[stderr]\n" + stderr;
    }

    private String readOutput(CompletableFuture<ProcessOutputCapture.CapturedOutput> outputFuture)
    {
        try
        {
            return outputFuture.get(2, TimeUnit.SECONDS).text();
        }
        catch (Exception ignored)
        {
            return "Output collection ended after the process timed out.";
        }
    }

    private List<String> buildCommand(MonitoringJob job) throws IOException
    {
        List<String> command = new ArrayList<>();
        command.add(scriptSecurityValidator.validate(job).toString());
        if (job.getCommandArguments() != null && !job.getCommandArguments().isBlank())
            command.addAll(parseArguments(job.getCommandArguments()));
        return command;
    }

    private List<String> parseArguments(String arguments)
    {
        List<String> result = new ArrayList<>();
        for (String argument : arguments.trim().split("\\s+"))
        {
            if (!argument.isBlank())
                result.add(argument);
        }
        return result;
    }
}
