package com.dxc.monitoring.service.executor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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
public class HealthCheckExecutor implements MonitoringExecutor
{
    @Override
    public MonitoringJob.MonitorType getType()
    {
        return MonitoringJob.MonitorType.HEALTH_CHECK;
    }

    @Override
    public MonitoringExecutionResult execute(MonitoringJob job)
    {
        MonitoringExecutionResult result = new MonitoringExecutionResult();
        result.setResultType(MonitoringResult.ResultType.STATUS);
        String type = job.getHealthCheckType() == null ? "TCP"
                : job.getHealthCheckType().trim().toUpperCase(Locale.ROOT);
        String host = job.getTargetHost();
        int timeoutSeconds = job.getTimeoutSeconds() == null ? 5 : Math.max(1, job.getTimeoutSeconds());

        try
        {
            if (host == null || host.isBlank())
                throw new IllegalArgumentException("Target host is required for health checks.");

            boolean healthy;
            String detail;
            switch (type)
            {
                case "TCP" ->
                {
                    if (job.getTargetPort() == null || job.getTargetPort() < 1 || job.getTargetPort() > 65535)
                        throw new IllegalArgumentException("A valid target port is required for TCP health checks.");
                    try (Socket socket = new Socket())
                    {
                        socket.connect(new InetSocketAddress(host.trim(), job.getTargetPort()),
                                Math.multiplyExact(timeoutSeconds, 1000));
                    }
                    healthy = true;
                    detail = "TCP connection succeeded to " + host.trim() + ":" + job.getTargetPort() + ".";
                }
                case "HTTP" ->
                {
                    if (job.getTargetPort() == null || job.getTargetPort() < 1 || job.getTargetPort() > 65535)
                        throw new IllegalArgumentException("A valid target port is required for HTTP health checks.");
                    URI uri = URI.create("http://" + host.trim() + ":" + job.getTargetPort() + "/");
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    HttpRequest request = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .GET().build();
                    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    healthy = response.statusCode() >= 200 && response.statusCode() < 400;
                    detail = "HTTP health check returned status " + response.statusCode() + ".";
                }
                case "PING" ->
                {
                    healthy = InetAddress.getByName(host.trim()).isReachable(timeoutSeconds * 1000);
                    detail = healthy ? "Host responded to reachability probe."
                            : "Host did not respond to reachability probe.";
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported health check type '" + type + "'. Use TCP, HTTP or PING.");
            }

            result.setExecutionStatus(healthy ? MonitoringExecution.ExecutionStatus.SUCCESS
                    : MonitoringExecution.ExecutionStatus.FAILED);
            result.setResultStatus(healthy ? MonitoringResult.ResultStatus.OK
                    : MonitoringResult.ResultStatus.FAILED);
            result.setValue(healthy ? "UP" : "DOWN");
            result.setMessage(detail);
            result.setRawOutput(detail);
            if (!healthy)
                result.setErrorMessage(detail);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            failed(result, "Health check was interrupted.", exception.getMessage());
        }
        catch (Exception exception)
        {
            failed(result, "Health check failed for " + (host == null ? "(no host)" : host) + ".", exception.getMessage());
        }
        return result;
    }

    private static void failed(MonitoringExecutionResult result, String message, String error)
    {
        result.setExecutionStatus(MonitoringExecution.ExecutionStatus.ERROR);
        result.setResultStatus(MonitoringResult.ResultStatus.FAILED);
        result.setValue("DOWN");
        result.setMessage(message);
        result.setErrorMessage(error);
        if (error != null)
            result.setRawOutput(error);
    }
}
