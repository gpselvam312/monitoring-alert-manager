package com.dxc.monitoring.service.executor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;

@Component
public class ApiExecutor implements MonitoringExecutor
{
    private final MonitoringResultNormalizer resultNormalizer;

    public ApiExecutor(MonitoringResultNormalizer resultNormalizer)
    {
        this.resultNormalizer = resultNormalizer;
    }

    @Override
    public MonitoringJob.MonitorType getType()
    {
        return MonitoringJob.MonitorType.API;
    }

    @Override
    public MonitoringExecutionResult execute(MonitoringJob job)
    {
        return execute(job, Map.of());
    }

    @Override
    public MonitoringExecutionResult execute(MonitoringJob job, Map<String, String> runtimeParameters)
    {
        Map<String, String> parameters = runtimeParameters == null ? Map.of() : runtimeParameters;
        MonitoringExecutionResult result = new MonitoringExecutionResult();
        result.setResultType(MonitoringResult.ResultType.TEXT);
        OffsetDateTime startedAt = OffsetDateTime.now();
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
                    .uri(URI.create(resolveTemplate(job.getUrl().trim(), parameters)))
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
                            builder.header(name, resolveTemplate(value, parameters));
                    }
                }
            }

            String body = job.getRequestBody();
            if ("GET".equals(method) || "HEAD".equals(method))
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            else
                builder.method(method, HttpRequest.BodyPublishers.ofString(resolveTemplate(body == null ? "" : body, parameters)));

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body() == null ? "" : response.body();
            result.setValue(Integer.toString(statusCode));
            result.setUnit("HTTP status");
            result.setRawOutput(responseBody);

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

            MonitoringResultNormalizer.NormalizedResult normalized = resultNormalizer.normalize(
                    responseBody, result.getExecutionStatus(), result.getResultStatus(), result.getMessage(),
                    startedAt, OffsetDateTime.now());
            result.setResultData(normalized.json());
            result.setResultType(normalized.resultType());
            result.setResultStatus(normalized.resultStatus());
            result.setMessage(normalized.message());
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            failed(result, "API request was interrupted.", exception.getMessage(), startedAt);
        }
        catch (Exception exception)
        {
            failed(result, "Unable to complete API monitoring request.", exception.getMessage(), startedAt);
        }
        return result;
    }

    private String resolveTemplate(String value, Map<String, String> parameters)
    {
        Matcher matcher = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}").matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find())
        {
            String parameterName = matcher.group(1);
            String parameterValue = parameters.get(parameterName);
            if (parameterValue == null)
            {
                throw new IllegalArgumentException("Missing runtime API parameter: " + parameterName);
            }
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(URLEncoder.encode(parameterValue, StandardCharsets.UTF_8)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private void failed(MonitoringExecutionResult result, String message, String error, OffsetDateTime startedAt)
    {
        result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
        result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
        result.setMessage(message);
        result.setErrorMessage(error);
        if (error != null)
            result.setRawOutput(error);

        MonitoringResultNormalizer.NormalizedResult normalized = resultNormalizer.normalize(
                error == null ? message : error, result.getExecutionStatus(), result.getResultStatus(),
                result.getMessage(), startedAt, OffsetDateTime.now());
        result.setResultData(normalized.json());
        result.setResultType(normalized.resultType());
    }
}
