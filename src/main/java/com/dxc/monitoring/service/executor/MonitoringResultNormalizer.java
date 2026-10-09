package com.dxc.monitoring.service.executor;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Converts JSON or text emitted by monitoring executors into the common
 * versioned result envelope consumed by dashboard widgets.
 */
@Component
public class MonitoringResultNormalizer
{
    private final ObjectMapper objectMapper;

    public MonitoringResultNormalizer(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public NormalizedResult normalize(String output, MonitoringExecution.ExecutionStatus executionStatus,
            MonitoringResult.ResultStatus fallbackStatus, String fallbackMessage, OffsetDateTime startedAt,
            OffsetDateTime completedAt)
    {
        Object parsed = null;
        boolean validJson = false;
        String source = output == null ? "" : output;

        if (!source.isBlank())
        {
            try
            {
                parsed = objectMapper.readValue(source, Object.class);
                validJson = true;
            }
            catch (Exception ignored)
            {
                // Legacy scripts and endpoints may still return plain text.
            }
        }

        Map<String, Object> envelope;
        MonitoringResult.ResultType resultType;
        MonitoringResult.ResultStatus resultStatus = fallbackStatus;
        String message = fallbackMessage;

        if (parsed instanceof Map<?, ?> parsedMap
                && parsedMap.containsKey("schemaVersion")
                && parsedMap.containsKey("data"))
        {
            envelope = objectMapper.convertValue(parsedMap, new TypeReference<Map<String, Object>>() {});
            envelope.putIfAbsent("schemaVersion", 1);
            envelope.putIfAbsent("executionState", executionState(executionStatus));
            envelope.putIfAbsent("startedAt", startedAt == null ? null : startedAt.toString());
            envelope.putIfAbsent("completedAt", completedAt == null ? null : completedAt.toString());

            Object sourceMessage = envelope.get("message");
            if (sourceMessage != null && !String.valueOf(sourceMessage).isBlank())
            {
                message = String.valueOf(sourceMessage);
            }
            else
            {
                envelope.put("message", fallbackMessage);
            }

            Object sourceStatus = envelope.get("status");
            MonitoringResult.ResultStatus parsedStatus = parseStatus(sourceStatus);
            if (parsedStatus != null)
            {
                resultStatus = parsedStatus;
                envelope.put("status", contractStatus(parsedStatus));
            }
            else if (sourceStatus != null && "UNKNOWN".equalsIgnoreCase(String.valueOf(sourceStatus)))
            {
                envelope.put("status", "UNKNOWN");
            }
            else
            {
                envelope.put("status", contractStatus(fallbackStatus));
            }

            resultType = resultTypeFor(envelope.get("data"));
        }
        else
        {
            Object data = validJson ? parsed : Map.of("text", source);
            envelope = new LinkedHashMap<>();
            envelope.put("schemaVersion", 1);
            envelope.put("executionState", executionState(executionStatus));
            envelope.put("status", contractStatus(fallbackStatus));
            envelope.put("message", fallbackMessage);
            if (startedAt != null)
            {
                envelope.put("startedAt", startedAt.toString());
            }
            if (completedAt != null)
            {
                envelope.put("completedAt", completedAt.toString());
            }
            envelope.put("data", data);
            resultType = resultTypeFor(data);
        }

        // A failed/timed-out execution must never be represented as healthy just
        // because its stdout happened to contain a success envelope.
        if (executionStatus != MonitoringExecution.ExecutionStatus.SUCCESS
                && fallbackStatus == MonitoringResult.ResultStatus.FAILED)
        {
            resultStatus = MonitoringResult.ResultStatus.FAILED;
            envelope.put("status", "FAILURE");
        }

        try
        {
            return new NormalizedResult(objectMapper.writeValueAsString(envelope), resultType, resultStatus, message);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Unable to serialize normalized monitoring result.", exception);
        }
    }

    public String attachExecutionId(String resultJson, Long executionId)
    {
        if (resultJson == null || resultJson.isBlank() || executionId == null)
        {
            return resultJson;
        }

        try
        {
            Map<String, Object> envelope =
                    objectMapper.readValue(resultJson, new TypeReference<Map<String, Object>>() {});
            envelope.putIfAbsent("executionId", executionId);
            return objectMapper.writeValueAsString(envelope);
        }
        catch (Exception exception)
        {
            return resultJson;
        }
    }

    private MonitoringResult.ResultType resultTypeFor(Object data)
    {
        if (data instanceof Map<?, ?>)
        {
            return MonitoringResult.ResultType.METRICS;
        }
        if (data instanceof Iterable<?>)
        {
            return MonitoringResult.ResultType.TABLE;
        }
        return MonitoringResult.ResultType.TEXT;
    }

    private MonitoringResult.ResultStatus parseStatus(Object value)
    {
        if (value == null)
        {
            return null;
        }

        return switch (String.valueOf(value).trim().toUpperCase())
        {
            case "SUCCESS", "OK", "GREEN", "HEALTHY" -> MonitoringResult.ResultStatus.OK;
            case "WARNING", "YELLOW" -> MonitoringResult.ResultStatus.WARNING;
            case "FAILURE", "FAILED", "RED", "CRITICAL" -> MonitoringResult.ResultStatus.FAILED;
            default -> null;
        };
    }

    private String contractStatus(MonitoringResult.ResultStatus status)
    {
        if (status == null)
        {
            return "UNKNOWN";
        }

        return switch (status)
        {
            case OK -> "SUCCESS";
            case WARNING -> "WARNING";
            case FAILED -> "FAILURE";
        };
    }

    private String executionState(MonitoringExecution.ExecutionStatus status)
    {
        if (status == null)
        {
            return "COMPLETED";
        }

        return switch (status)
        {
            case RUNNING -> "RUNNING";
            case TIMEOUT -> "TIMED_OUT";
            case FAILED, ERROR -> "FAILED";
            case SUCCESS -> "COMPLETED";
        };
    }

    public record NormalizedResult(String json, MonitoringResult.ResultType resultType,
            MonitoringResult.ResultStatus resultStatus, String message)
    {
    }
}
