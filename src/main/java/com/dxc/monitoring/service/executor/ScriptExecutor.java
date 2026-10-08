package com.dxc.monitoring.service.executor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

        List<String> command = buildCommand(job);

        Process process = null;

        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder(command);

            if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
            {

                processBuilder.directory(new java.io.File(job.getWorkingDirectory()));
            }

            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();

            int timeoutSeconds = job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 30;

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed)
            {
                process.destroyForcibly();

                result.setExecutionStatus(MonitoringExecution.ExecutionStatus.TIMEOUT);

                result.setResultType(MonitoringResult.ResultType.TEXT);

                result.setResultStatus(MonitoringResult.ResultStatus.FAILED);

                result.setMessage("Script execution timed out after " + timeoutSeconds + " seconds.");

                result.setErrorMessage("Script execution timed out.");

                return result;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.exitValue();

            result.setRawOutput(output);

            result.setResultType(MonitoringResult.ResultType.TEXT);

            if (exitCode == 0)
            {

                result.setExecutionStatus(MonitoringExecution.ExecutionStatus.SUCCESS);

                result.setResultStatus(MonitoringResult.ResultStatus.OK);

                result.setMessage("Script completed successfully.");

            } else
            {

                result.setExecutionStatus(MonitoringExecution.ExecutionStatus.FAILED);

                result.setResultStatus(MonitoringResult.ResultStatus.FAILED);

                result.setMessage("Script failed with exit code " + exitCode);

                result.setErrorMessage("Script exited with code " + exitCode);
            }

            return result;

        } catch (InterruptedException ex)
        {

            Thread.currentThread().interrupt();

            if (process != null)
            {
                process.destroyForcibly();
            }

            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);

            result.setResultType(MonitoringResult.ResultType.TEXT);

            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);

            result.setMessage("Script execution was interrupted.");

            result.setErrorMessage(ex.getMessage());

            return result;

        } catch (IOException ex)
        {

            result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);

            result.setResultType(MonitoringResult.ResultType.TEXT);

            result.setResultStatus(MonitoringResult.ResultStatus.FAILED);

            result.setMessage("Unable to execute monitoring script.");

            result.setErrorMessage(ex.getMessage());

            return result;
        }
    }

    private List<String> buildCommand(MonitoringJob job)
    {

        if (job.getScriptPath() == null || job.getScriptPath().isBlank())
        {

            throw new IllegalArgumentException("Script path is required for SCRIPT monitoring job.");
        }

        List<String> command = new ArrayList<>();

        command.add(job.getScriptPath());

        if (job.getCommandArguments() != null && !job.getCommandArguments().isBlank())
        {

            command.addAll(parseArguments(job.getCommandArguments()));
        }

        return command;
    }

    private List<String> parseArguments(String arguments)
    {

        List<String> result = new ArrayList<>();

        for (String argument : arguments.trim().split("\\s+"))
        {
            if (!argument.isBlank())
            {
                result.add(argument);
            }
        }

        return result;
    }
}