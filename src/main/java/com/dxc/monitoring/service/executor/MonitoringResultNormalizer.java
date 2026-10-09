package com.dxc.monitoring.service.executor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return normalize(output, executionStatus, fallbackStatus, fallbackMessage, startedAt, completedAt, "{}");
    }

    public NormalizedResult normalize(String output, MonitoringExecution.ExecutionStatus executionStatus,
            MonitoringResult.ResultStatus fallbackStatus, String fallbackMessage, OffsetDateTime startedAt,
            OffsetDateTime completedAt, String parserConfigJson)
    {
        Object parsed = null;
        boolean validJson = false;
        String source = output == null ? "" : output;
        Map<String, Object> parserConfig = parseParserConfig(parserConfigJson);
        String parserType = String.valueOf(parserConfig.getOrDefault("type", "AUTO")).trim().toUpperCase(Locale.ROOT);

        if (!source.isBlank())
        {
            try
            {
                if ("DELIMITED".equals(parserType))
                {
                    parsed = parseDelimited(source, parserConfig);
                    validJson = true;
                }
                else if ("KEY_VALUE".equals(parserType))
                {
                    parsed = parseKeyValue(source, parserConfig);
                    validJson = true;
                }
                else if ("REGEX".equals(parserType))
                {
                    parsed = parseRegex(source, parserConfig);
                    validJson = true;
                }
                else
                {
                    parsed = objectMapper.readValue(source, Object.class);
                    validJson = true;
                }
            }
            catch (Exception exception)
            {
                if ("AUTO".equals(parserType))
                {
                    // Automatic mode keeps compatibility with plain-text outputs.
                }
                else
                {
                    parsed = Map.of("parserError", exception.getMessage() == null
                            ? "Configured parser could not read the output." : exception.getMessage());
                    validJson = true;
                    fallbackStatus = MonitoringResult.ResultStatus.FAILED;
                    fallbackMessage = "Unable to parse monitoring output using the configured parser.";
                }
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

    public void validateParserConfig(String parserConfigJson)
    {
        if (parserConfigJson == null || parserConfigJson.isBlank())
        {
            return;
        }

        Map<String, Object> config;
        try
        {
            config = objectMapper.readValue(parserConfigJson, new TypeReference<Map<String, Object>>() {});
        }
        catch (Exception exception)
        {
            throw new IllegalArgumentException("Result parser configuration must be a valid JSON object.");
        }
        if (config == null)
        {
            throw new IllegalArgumentException("Result parser configuration must be a JSON object.");
        }

        String type = String.valueOf(config.getOrDefault("type", "AUTO")).trim().toUpperCase(Locale.ROOT);
        if (!List.of("AUTO", "JSON", "DELIMITED", "KEY_VALUE", "REGEX").contains(type))
        {
            throw new IllegalArgumentException("Unsupported result parser type: " + type);
        }
        if ("DELIMITED".equals(type)
                && (!(config.get("delimiter") instanceof String delimiter) || delimiter.isEmpty()))
        {
            throw new IllegalArgumentException("Delimited parser requires a non-empty delimiter.");
        }
        if ("KEY_VALUE".equals(type)
                && config.containsKey("separator")
                && (!(config.get("separator") instanceof String separator) || separator.isEmpty()))
        {
            throw new IllegalArgumentException("Key-value parser separator must not be empty.");
        }
        if ("REGEX".equals(type))
        {
            if (!(config.get("fields") instanceof List<?> fields) || fields.isEmpty())
            {
                throw new IllegalArgumentException("Regex parser requires a non-empty fields array.");
            }
            for (Object fieldObject : fields)
            {
                if (!(fieldObject instanceof Map<?, ?> field)
                        || field.get("key") == null || field.get("pattern") == null)
                {
                    throw new IllegalArgumentException("Each regex field requires key and pattern.");
                }
                try
                {
                    Pattern.compile(String.valueOf(field.get("pattern")), Pattern.MULTILINE);
                }
                catch (Exception exception)
                {
                    throw new IllegalArgumentException("Invalid regex pattern for field: " + field.get("key"));
                }
            }
        }
    }

    private Map<String, Object> parseParserConfig(String parserConfigJson)
    {
        if (parserConfigJson == null || parserConfigJson.isBlank())
        {
            return Map.of();
        }
        try
        {
            Map<String, Object> config = objectMapper.readValue(
                    parserConfigJson, new TypeReference<Map<String, Object>>() {});
            return config == null ? Map.of() : config;
        }
        catch (Exception ignored)
        {
            return Map.of();
        }
    }

    private Map<String, Object> parseDelimited(String source, Map<String, Object> config)
    {
        String delimiter = String.valueOf(config.getOrDefault("delimiter", "|"));
        if (delimiter.isEmpty())
        {
            throw new IllegalArgumentException("Delimited parser requires a non-empty delimiter.");
        }

        boolean headerRow = !Boolean.FALSE.equals(config.get("headerRow"));
        boolean ignoreBlankLines = !Boolean.FALSE.equals(config.get("ignoreBlankLines"));
        boolean trim = !Boolean.FALSE.equals(config.get("trim"));
        String commentPrefix = config.get("commentPrefix") instanceof String prefix ? prefix : "";
        List<String[]> records = new ArrayList<>();
        for (String line : source.split("\\R"))
        {
            if ((ignoreBlankLines && line.isBlank())
                    || (!commentPrefix.isEmpty() && line.trim().startsWith(commentPrefix)))
            {
                continue;
            }
            String[] fields = line.split(Pattern.quote(delimiter), -1);
            if (trim)
            {
                for (int i = 0; i < fields.length; i++)
                {
                    fields[i] = fields[i].trim();
                }
            }
            records.add(fields);
        }

        if (records.isEmpty())
        {
            return Map.of("columns", List.of(), "rows", List.of());
        }

        String[] headers;
        int firstDataRecord;
        if (headerRow)
        {
            headers = records.get(0);
            firstDataRecord = 1;
        }
        else
        {
            headers = new String[records.stream().mapToInt(record -> record.length).max().orElse(0)];
            for (int i = 0; i < headers.length; i++)
            {
                headers[i] = "column" + (i + 1);
            }
            firstDataRecord = 0;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int rowIndex = firstDataRecord; rowIndex < records.size(); rowIndex++)
        {
            String[] values = records.get(rowIndex);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.length; columnIndex++)
            {
                String key = headers[columnIndex].isBlank() ? "column" + (columnIndex + 1) : headers[columnIndex];
                String value = columnIndex < values.length ? values[columnIndex] : "";
                row.put(key, convertConfiguredValue(value, config, key));
            }
            rows.add(row);
        }

        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 0; i < headers.length; i++)
        {
            String key = headers[i].isBlank() ? "column" + (i + 1) : headers[i];
            columns.add(Map.of("key", key, "label", key));
        }

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("columns", columns);
        table.put("rows", rows);
        return table;
    }

    private Map<String, Object> parseKeyValue(String source, Map<String, Object> config)
    {
        String separator = String.valueOf(config.getOrDefault("separator", ":"));
        if (separator.isEmpty())
        {
            throw new IllegalArgumentException("Key-value parser requires a non-empty separator.");
        }

        List<String> ignoredPrefixes = new ArrayList<>();
        if (config.get("ignorePrefixes") instanceof List<?> prefixes)
        {
            prefixes.forEach(prefix -> ignoredPrefixes.add(String.valueOf(prefix)));
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (String line : source.split("\\R"))
        {
            if (line.isBlank() || ignoredPrefixes.stream().anyMatch(line.trim()::startsWith))
            {
                continue;
            }
            int separatorIndex = line.indexOf(separator);
            if (separatorIndex <= 0)
            {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + separator.length()).trim();
            values.put(key, convertConfiguredValue(value, config, key));
        }
        if (values.isEmpty())
        {
            throw new IllegalArgumentException("No key-value entries were found in the output.");
        }
        return values;
    }

    private Map<String, Object> parseRegex(String source, Map<String, Object> config)
    {
        if (!(config.get("fields") instanceof List<?> fields) || fields.isEmpty())
        {
            throw new IllegalArgumentException("Regex parser requires a non-empty fields array.");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (Object fieldObject : fields)
        {
            if (!(fieldObject instanceof Map<?, ?> field))
            {
                throw new IllegalArgumentException("Each regex field must be a JSON object.");
            }

            String key = String.valueOf(field.get("key"));
            Object patternValue = field.get("pattern");
            if (key.isBlank() || patternValue == null)
            {
                throw new IllegalArgumentException("Each regex field requires key and pattern.");
            }

            Pattern pattern = Pattern.compile(String.valueOf(patternValue), Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(source);
            if (!matcher.find())
            {
                if (Boolean.TRUE.equals(field.get("required")))
                {
                    throw new IllegalArgumentException("Required regex field was not found: " + key);
                }
                continue;
            }

            int group = field.get("group") instanceof Number number ? number.intValue() : 1;
            if (group < 0 || group > matcher.groupCount())
            {
                throw new IllegalArgumentException("Invalid regex group for field: " + key);
            }
            values.put(key, convertValue(matcher.group(group), field.get("type")));
        }

        if (values.isEmpty())
        {
            throw new IllegalArgumentException("No configured regex fields matched the output.");
        }
        return values;
    }

    private Object convertConfiguredValue(String value, Map<String, Object> config, String key)
    {
        Object types = config.get("types");
        if (types instanceof Map<?, ?> typeMap)
        {
            return convertValue(value, typeMap.get(key));
        }
        return value;
    }

    private Object convertValue(String value, Object typeValue)
    {
        if (value == null)
        {
            return null;
        }
        String type = typeValue == null ? "string" : String.valueOf(typeValue).toLowerCase(Locale.ROOT);
        String normalized = value.trim().replace(",", "");
        if (normalized.isEmpty())
        {
            return "";
        }
        try
        {
            return switch (type)
            {
                case "integer", "long" -> Long.valueOf(normalized.replace("%", ""));
                case "number", "decimal", "percentage" -> Double.valueOf(normalized.replace("%", ""));
                case "boolean" -> Boolean.valueOf(normalized);
                default -> value;
            };
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException("Value for parser field is not a valid " + type + ": " + value);
        }
    }

    private MonitoringResult.ResultType resultTypeFor(Object data)
    {
        if (data instanceof Map<?, ?> dataMap)
        {
            if (dataMap.get("rows") instanceof Iterable<?>)
            {
                return MonitoringResult.ResultType.TABLE;
            }
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
