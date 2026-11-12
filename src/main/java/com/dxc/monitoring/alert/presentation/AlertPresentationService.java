package com.dxc.monitoring.alert.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dxc.monitoring.alert.json.JsonDocument;
import com.dxc.monitoring.alert.json.JsonPathResolver;
import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.databind.ObjectMapper;

/** Builds one channel-neutral presentation from the rule and triggering execution response. */
@Service
public class AlertPresentationService {
    private static final Logger log = LoggerFactory.getLogger(AlertPresentationService.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final int MAX_SECTION_TEXT = 10_000;

    private final ObjectMapper objectMapper;
    private final MonitoringResultRepository resultRepository;
    private final JsonPathResolver pathResolver = new JsonPathResolver();

    public AlertPresentationService(ObjectMapper objectMapper, MonitoringResultRepository resultRepository) {
        this.objectMapper = objectMapper;
        this.resultRepository = resultRepository;
    }

    public Presentation build(AlertHistory history, Map<String, Object> notificationPayload) {
        Alert alert = history == null ? null : history.getAlert();
        AlertRule rule = alert == null ? null : alert.getAlertRule();
        if (rule == null || rule.getPresentationConfig() == null || rule.getPresentationConfig().isBlank()) return null;
        try {
            Map<String, Object> config = objectMapper.readValue(rule.getPresentationConfig(), Map.class);
            if (config == null || config.isEmpty()) return null;
            Object rawSections = config.get("sections");
            List<?> sections = rawSections instanceof List<?> list ? list : List.of();
            String titleTemplate = text(config.get("titleTemplate"));
            String messageTemplate = text(config.get("messageTemplate"));
            if (sections.isEmpty() && titleTemplate.isBlank() && messageTemplate.isBlank()) return null;

            Map<String, Object> templateValues = templateValues(alert, history, notificationPayload);
            JsonDocument document = responseDocument(history);
            List<Map<String, Object>> renderedSections = new ArrayList<>();
            for (Object rawSection : sections) {
                if (rawSection instanceof Map<?, ?> section) {
                    Map<String, Object> rendered = renderSection(section, document, templateValues);
                    if (rendered != null) renderedSections.add(rendered);
                }
            }
            String title = titleTemplate.isBlank()
                    ? text(notificationPayload.get("title")) : renderTemplate(titleTemplate, templateValues);
            String message = messageTemplate.isBlank()
                    ? text(notificationPayload.get("body")) : renderTemplate(messageTemplate, templateValues);
            return new Presentation(title, message, renderedSections);
        } catch (Exception exception) {
            log.warn("Alert presentation configuration could not be rendered for alertId={}; using legacy message content",
                    alert == null ? null : alert.getId(), exception);
            return null;
        }
    }

    private Map<String, Object> templateValues(Alert alert, AlertHistory history,
            Map<String, Object> payload) {
        Map<String, Object> values = new LinkedHashMap<>(payload);
        values.put("ruleName", alert.getAlertRule() == null ? "" : alert.getAlertRule().getName());
        values.put("severity", history.getNewSeverity() == null
                ? alert.getSeverity().name() : history.getNewSeverity().name());
        values.put("jobName", alert.getMonitoringJob() == null ? "" : alert.getMonitoringJob().getName());
        values.put("applicationName", alert.getMonitoringJob() == null
                || alert.getMonitoringJob().getApplication() == null ? "" : alert.getMonitoringJob().getApplication().getName());
        values.put("environment", alert.getMonitoringJob() == null
                || alert.getMonitoringJob().getEnvironment() == null ? "" : alert.getMonitoringJob().getEnvironment().getName());
        values.put("actualValue", payload.getOrDefault("observedValue", ""));
        values.put("timestamp", history.getOccurredAt() == null ? "" : history.getOccurredAt().toString());
        values.put("executionId", history.getExecution() == null ? "" : history.getExecution().getId());
        values.put("alertId", alert.getId());
        values.put("occurrenceCount", alert.getOccurrenceCount());
        return values;
    }

    private JsonDocument responseDocument(AlertHistory history) {
        MonitoringExecution execution = history == null ? null : history.getExecution();
        if (execution == null || execution.getId() == null) return JsonDocument.from(Map.of());
        List<MonitoringResult> results = resultRepository.findAllByExecutionId(execution.getId());
        MonitoringResult latest = results.stream()
                .max(Comparator.comparing(MonitoringResult::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(MonitoringResult::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (latest == null) return JsonDocument.from(Map.of());

        Object parsed = parse(latest.getResultData());
        if (parsed == null) parsed = parse(latest.getRawOutput());
        if (parsed == null) {
            String message = firstNonBlank(latest.getMessage(), latest.getRawOutput());
            parsed = parseKeyValue(message);
        }
        return JsonDocument.from(parsed == null ? Map.of() : parsed);
    }

    private Object parse(String source) {
        if (source == null || source.isBlank() || source.length() > 1_000_000) return null;
        try {
            return objectMapper.readValue(source, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parseKeyValue(String source) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (source == null || source.isBlank()) return values;
        for (String line : source.split("\\\\R")) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,100}")) values.put(key, value);
        }
        if (values.isEmpty()) values.put("message", source.length() > 10_000 ? source.substring(0, 10_000) : source);
        return values;
    }

    private Map<String, Object> renderSection(Map<?, ?> section, JsonDocument document,
            Map<String, Object> templateValues) {
        String type = text(section.get("type")).toUpperCase(Locale.ROOT);
        String title = renderTemplate(text(section.get("title")), templateValues);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type.toLowerCase(Locale.ROOT));
        if (!title.isBlank()) result.put("title", title);
        switch (type) {
            case "TEXT" -> result.put("text", renderTemplate(text(section.get("text")), templateValues));
            case "KEY_VALUE" -> {
                List<Map<String, String>> fields = new ArrayList<>();
                Object rawFields = section.get("fields");
                if (rawFields instanceof List<?> list) {
                    for (Object rawField : list) {
                        if (!(rawField instanceof Map<?, ?> field)) continue;
                        String path = text(field.get("path"));
                        JsonPathResolver.ResolvedValues resolved = resolve(document, path);
                        if (resolved.found()) {
                            fields.add(Map.of("label", renderTemplate(text(field.get("label")), templateValues),
                                    "value", displayValue(observed(resolved.values()))));
                        }
                    }
                }
                if (fields.isEmpty()) return null;
                result.put("fields", fields);
            }
            case "TABLE" -> {
                Object source = sourceValue(document, text(section.get("sourcePath")));
                if (!(source instanceof List<?> rows) || rows.isEmpty()) {
                    return emptyText(section, title);
                }
                Object rawColumns = section.get("columns");
                if (!(rawColumns instanceof List<?> columns) || columns.isEmpty()) return null;
                List<Map<String, String>> normalizedColumns = new ArrayList<>();
                for (Object rawColumn : columns) {
                    if (rawColumn instanceof Map<?, ?> column) {
                        normalizedColumns.add(Map.of("key", text(column.get("key")), "label", text(column.get("label"))));
                    }
                }
                int maxRows = integer(section.get("maxRows"), 20);
                List<Map<String, Object>> tableRows = new ArrayList<>();
                for (Object row : rows.stream().limit(maxRows).toList()) {
                    Map<String, Object> tableRow = new LinkedHashMap<>();
                    for (Map<String, String> column : normalizedColumns) {
                        tableRow.put(column.get("key"), resolveRelative(row, column.get("key")));
                    }
                    tableRows.add(tableRow);
                }
                result.put("columns", normalizedColumns);
                result.put("rows", tableRows);
                result.put("truncated", rows.size() > maxRows);
            }
            case "LIST" -> {
                Object source = sourceValue(document, text(section.get("sourcePath")));
                if (!(source instanceof List<?> items) || items.isEmpty()) return emptyText(section, title);
                int maxItems = integer(section.get("maxItems"), 20);
                String valuePath = text(section.get("valuePath"));
                List<String> values = new ArrayList<>();
                for (Object item : items.stream().limit(maxItems).toList()) {
                    Object value = valuePath.isBlank() ? item : resolveRelative(item, valuePath);
                    values.add(displayValue(value));
                }
                result.put("items", values);
                result.put("truncated", items.size() > maxItems);
            }
            case "JSON" -> {
                Object source = sourceValue(document, text(section.get("sourcePath")));
                if (source == null) return emptyText(section, title);
                String json = objectMapper.writeValueAsString(source);
                int maxLength = integer(section.get("maxLength"), 4000);
                result.put("value", json.length() > maxLength
                        ? json.substring(0, maxLength) + "… (truncated)" : json);
            }
            default -> { return null; }
        }
        return result;
    }

    private Map<String, Object> emptyText(Map<?, ?> section, String title) {
        String emptyText = text(section.get("emptyText"));
        if (emptyText.isBlank()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "text");
        if (!title.isBlank()) result.put("title", title);
        result.put("text", emptyText);
        return result;
    }

    private Object sourceValue(JsonDocument document, String path) {
        JsonPathResolver.ResolvedValues resolved = resolve(document, path);
        if (!resolved.found() || resolved.values().size() != 1) return null;
        return resolved.values().get(0);
    }

    private JsonPathResolver.ResolvedValues resolve(JsonDocument document, String path) {
        if (path == null || path.isBlank()) return new JsonPathResolver.ResolvedValues(false, List.of(), false);
        return pathResolver.resolve(document, path);
    }

    private Object resolveRelative(Object root, String path) {
        if (path == null || path.isBlank()) return root;
        Object current = root;
        for (String segment : path.split("\\\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    private Object observed(List<Object> values) {
        if (values.size() == 1) return values.get(0);
        return values;
    }

    private String renderTemplate(String template, Map<String, Object> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object replacement = values.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    replacement == null ? "" : displayValue(replacement)));
        }
        matcher.appendTail(output);
        return output.length() > MAX_SECTION_TEXT ? output.substring(0, MAX_SECTION_TEXT) : output.toString();
    }

    private String displayValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try { return objectMapper.writeValueAsString(value); }
            catch (Exception ignored) { return String.valueOf(value); }
        }
        return String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    public record Presentation(String title, String message, List<Map<String, Object>> sections) {
        public Presentation {
            sections = List.copyOf(sections);
        }
    }
}
