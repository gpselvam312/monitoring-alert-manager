package com.dxc.monitoring.service.executor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;

@Component
public class ScriptExecutor implements MonitoringExecutor
{
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
        Process process = null;

        try
        {
            ProcessBuilder builder = new ProcessBuilder(buildCommand(job)).redirectErrorStream(true);
            if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
                builder.directory(new File(job.getWorkingDirectory()));

            process = builder.start();
            Process runningProcess = process;

            // Drain output while the process runs to avoid filling the OS pipe and deadlocking.
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() ->
            {
                try
                {
                    return runningProcess.getInputStream().readAllBytes();
                }
                catch (IOException exception)
                {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            });

            int timeoutSeconds = job.getTimeoutSeconds() == null ? 30 : Math.max(1, job.getTimeoutSeconds());
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed)
            {
                process.destroyForcibly();
                process.waitFor();
                result.setExecutionStatus(MonitoringExecution.ExecutionStatus.TIMEOUT);
                result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
                result.setMessage("Script execution timed out after " + timeoutSeconds + " seconds.");
                result.setErrorMessage("Script execution timed out.");
                result.setRawOutput(readOutput(outputFuture));
                return result;
            }

            String output = new String(outputFuture.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            result.setRawOutput(output);
            int exitCode = process.exitValue();

            String expected = job.getExpectedResult();
            boolean expectedMatches = expected == null || expected.isBlank() || output.contains(expected);
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
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            if (process != null)
                process.destroyForcibly();
            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
            result.setMessage("Script execution was interrupted.");
            result.setErrorMessage(exception.getMessage());
        }
        catch (IOException | ExecutionException | TimeoutException exception)
        {
            if (process != null)
                process.destroyForcibly();
            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
            result.setMessage("Unable to execute or collect output from the monitoring script.");
            result.setErrorMessage(exception.getMessage());
        }
        return result;
    }

    private String readOutput(CompletableFuture<byte[]> outputFuture)
    {
        try
        {
            return new String(outputFuture.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        }
        catch (Exception ignored)
        {
            return "Output collection ended after the process timed out.";
        }
    }

    private List<String> buildCommand(MonitoringJob job)
    {
        if (job.getScriptPath() == null || job.getScriptPath().isBlank())
            throw new IllegalArgumentException("Script path is required for SCRIPT monitoring job.");

        List<String> command = new ArrayList<>();
        command.add(job.getScriptPath());
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
