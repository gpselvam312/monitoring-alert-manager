package com.dxc.monitoring.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Discovers selectable paths from the most recent successful, persisted execution.
 * This service deliberately never starts a monitoring job.
 */
@Service
public class AlertRuleFieldDiscoveryService {
    private static final int MAX_FIELDS = 500;
    private static final int MAX_SAMPLE_LENGTH = 120;

    private final MonitoringExecutionRepository executionRepository;
    private final MonitoringResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    public AlertRuleFieldDiscoveryService(MonitoringExecutionRepository executionRepository,
            MonitoringResultRepository resultRepository, ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Discovery discover(MonitoringJob job) {
        MonitoringExecution execution = executionRepository
                .findFirstByMonitoringJob_IdAndStatusOrderByStartedAtDescIdDesc(
                        job.getId(), MonitoringExecution.ExecutionStatus.SUCCESS)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No successful saved execution is available for this job. Run it successfully and try again."));
        List<MonitoringResult> results = resultRepository.findAllByExecutionId(execution.getId()).stream()
                .sorted(Comparator.comparing(MonitoringResult::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MonitoringResult::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (results.isEmpty()) {
            throw new IllegalArgumentException(
                    "The latest successful execution has no saved result. Check that result storage is enabled for this job.");
        }

        MonitoringResult result = results.get(results.size() - 1);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("executionStatus", execution.getStatus().name());
        root.put("triggerSource", execution.getTriggerSource().name());
        if (result.getStatus() != null) root.put("resultStatus", result.getStatus().name());
        if (result.getValue() != null) root.put("resultValue", result.getValue());
        if (result.getUnit() != null) root.put("resultUnit", result.getUnit());

        // The result message is the user-facing monitoring payload for many job types.
        // Prefer it for field discovery so the choices match the data users see.
        Object messagePayload = parseStructured(result.getMessage());
        boolean messagePayloadFound = false;
        if (messagePayload instanceof Map<?, ?> messageMap) {
            messagePayloadFound = true;
            messageMap.forEach((key, value) -> root.put(String.valueOf(key), value));
        } else if (messagePayload != null) {
            messagePayloadFound = true;
            root.put("data", messagePayload);
        } else if (result.getMessage() != null && !result.getMessage().isBlank()) {
            Map<String, Object> messageFields = parseKeyValueLines(result.getMessage());
            if (!messageFields.isEmpty()) {
                messagePayloadFound = true;
                root.putAll(messageFields);
            } else {
                root.put("message", result.getMessage());
            }
        }

        // Keep normalized result data as a fallback for result types whose payload
        // is stored there instead of in the message column.
        if (!messagePayloadFound) {
            Object parsed = parseStructured(result.getResultData());
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((key, value) -> root.put(String.valueOf(key), value));
            } else if (parsed != null) {
                root.put("data", parsed);
            }
        }

        List<Field> fields = new ArrayList<>();
        flatten(root, "", fields);
        fields.sort(Comparator.comparing(Field::path, String.CASE_INSENSITIVE_ORDER));
        return new Discovery(execution.getId(), execution.getStartedAt() == null ? "" : execution.getStartedAt().toString(),
                execution.getTriggerSource().name(), fields);
    }

    public Discovery discoverSample(String sample) {
        if (sample == null || sample.isBlank()) {
            throw new IllegalArgumentException("Paste a sample response before discovering fields.");
        }
        Object parsed = parseStructured(sample);
        Map<String, Object> root = new LinkedHashMap<>();
        if (parsed instanceof Map<?, ?> map) {
            map.forEach((key, value) -> root.put(String.valueOf(key), value));
        } else if (parsed != null) {
            root.put("data", parsed);
        } else {
            root.putAll(parseKeyValueLines(sample));
            if (root.isEmpty()) {
                String[] lines = sample.strip().split("\\R");
                if (lines.length >= 2 && lines[0].contains(",")) {
                    String[] headers = lines[0].split(",", -1);
                    String[] values = lines[1].split(",", -1);
                    for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                        String key = headers[i].trim();
                        if (key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,254}")) {
                            root.put(key, coerce(values[i].trim()));
                        }
                    }
                }
            }
        }
        if (root.isEmpty()) {
            throw new IllegalArgumentException("No fields could be discovered. Use JSON, key-value lines, or comma-delimited header/value rows.");
        }
        List<Field> fields = new ArrayList<>();
        flatten(root, "", fields);
        fields.sort(Comparator.comparing(Field::path, String.CASE_INSENSITIVE_ORDER));
        return new Discovery(null, "Sample response (not saved)", "SAMPLE", fields);
    }

    private Object parseStructured(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parseKeyValueLines(String text) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator < 1) separator = line.indexOf(':');
            if (separator < 1) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,254}") && !value.isEmpty()) {
                values.put(key, coerce(value));
            }
        }
        return values;
    }

    private Object coerce(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return Boolean.valueOf(value);
        try { return new java.math.BigDecimal(value); } catch (NumberFormatException ignored) { return value; }
    }

    private void flatten(Object value, String path, List<Field> fields) {
        if (fields.size() >= MAX_FIELDS || path.length() > 255) return;
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.isBlank()) continue;
                String child = path.isEmpty() ? key : path + "." + key;
                flatten(entry.getValue(), child, fields);
                if (fields.size() >= MAX_FIELDS) return;
            }
            return;
        }
        if (value instanceof List<?>) {
            return;
        }
        if (!path.isEmpty()) {
            String type = value == null ? "null"
                    : value instanceof Number ? "number"
                    : value instanceof Boolean ? "boolean" : "string";
            String sample = value == null ? "null" : String.valueOf(value);
            if (sample.length() > MAX_SAMPLE_LENGTH) sample = sample.substring(0, MAX_SAMPLE_LENGTH) + "…";
            fields.add(new Field(path, path, type, sample));
        }
    }

    public record Field(String path, String label, String type, String sampleValue) {}
    public record Discovery(Long executionId, String executionStartedAt, String triggerSource, List<Field> fields) {}
}
