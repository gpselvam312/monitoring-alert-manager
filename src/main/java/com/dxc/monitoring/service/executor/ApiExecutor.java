package com.dxc.monitoring.service.executor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;

@Component
public class ApiExecutor implements MonitoringExecutor
{
    @Override
    public MonitoringJob.MonitorType getType()
    {
        return MonitoringJob.MonitorType.API;
    }

    @Override
    public MonitoringExecutionResult execute(MonitoringJob job)
    {
        MonitoringExecutionResult result = new MonitoringExecutionResult();
        result.setResultType(MonitoringResult.ResultType.TEXT);
        try
        {
            if (job.getUrl() == null || job.getUrl().isBlank())
                throw new IllegalArgumentException("URL is required for API monitoring.");

            int timeoutSeconds = job.getTimeoutSeconds() == null ? 30 : Math.max(1, job.getTimeoutSeconds());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            String method = job.getHttpMethod() == null || job.getHttpMethod().isBlank()
                    ? "GET" : job.getHttpMethod().trim().toUpperCase(Locale.ROOT);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(job.getUrl().trim()))
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            String headers = job.getRequestHeaders();
            if (headers != null && !headers.isBlank())
            {
                for (String line : headers.split("\\R"))
                {
                    int separator = line.indexOf(':');
                    if (separator > 0)
                    {
                        String name = line.substring(0, separator).trim();
                        String value = line.substring(separator + 1).trim();
                        if (!name.isEmpty())
                            builder.header(name, value);
                    }
                }
            }

            String body = job.getRequestBody();
            if ("GET".equals(method) || "HEAD".equals(method))
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            else
                builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body() == null ? "" : response.body();
            result.setValue(Integer.toString(statusCode));
            result.setUnit("HTTP status");
            result.setRawOutput(responseBody);
            result.setResultData(responseBody);

            int expectedStatus = job.getExpectedHttpStatus() == null ? 200 : job.getExpectedHttpStatus();
            boolean statusMatches = statusCode == expectedStatus;
            String expectedResponse = job.getExpectedResponse();
            boolean bodyMatches = expectedResponse == null || expectedResponse.isBlank()
                    || responseBody.contains(expectedResponse);
            boolean success = statusMatches && bodyMatches;

            result.setExecutionStatus(success ? MonitoringExecution.ExecutionStatus.SUCCESS
                    : MonitoringExecution.ExecutionStatus.FAILED);
            result.setResultStatus(success ? MonitoringResult.ResultStatus.OK
                    : MonitoringResult.ResultStatus.FAILED);
            result.setMessage(success
                    ? "API returned the expected HTTP status and response."
                    : "API response did not match the configured expectation (HTTP " + statusCode
                            + ", expected " + expectedStatus
                            + (bodyMatches ? "" : "; expected response text was not found") + ").");
            if (!success)
                result.setErrorMessage(result.getMessage());
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            failed(result, "API request was interrupted.", exception.getMessage());
        }
        catch (Exception exception)
        {
            failed(result, "Unable to complete API monitoring request.", exception.getMessage());
        }
        return result;
    }

    private static void failed(MonitoringExecutionResult result, String message, String error)
    {
        result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
        result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
        result.setMessage(message);
        result.setErrorMessage(error);
        if (error != null)
            result.setRawOutput(error);
    }
}
