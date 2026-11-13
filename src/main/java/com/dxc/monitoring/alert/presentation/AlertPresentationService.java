package com.dxc.monitoring.alert.presentation;

import java.util.ArrayList;
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
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{?([A-Za-z][A-Za-z0-9_.-]*)\\}\\}?");
    private static final int MAX_SECTION_TEXT = 10_000;
    private static final int MAX_PRESENTATION_SIZE = 12_000;
    private static final int MAX_DISPLAY_VALUE = 1_000;

    private final ObjectMapper objectMapper;
    private final MonitoringResultRepository resultRepository;
    private final JsonPathResolver pathResolver = new JsonPathResolver();

    public AlertPresentationService(ObjectMapper objectMapper, MonitoringResultRepository resultRepository) {
        this.objectMapper = objectMapper;
        this.resultRepository = resultRepository;
    }

    public Presentation buildTemplates(AlertHistory history, Map<String, Object> notificationPayload) {
        Alert alert = history == null ? null : history.getAlert();
        AlertRule rule = alert == null ? null : alert.getAlertRule();
        if (rule == null || rule.getPresentationConfig() == null || rule.getPresentationConfig().isBlank()) return null;
        try {
            Map<String, Object> config = objectMapper.readValue(rule.getPresentationConfig(), Map.class);
            if (config == null || config.isEmpty() || !supportedVersion(config)) return null;
            String titleTemplate = text(config.get("titleTemplate"));
            String messageTemplate = text(config.get("messageTemplate"));
            if (titleTemplate.isBlank() && messageTemplate.isBlank()) return null;
            Map<String, Object> values = templateValues(alert, history, notificationPayload);
            JsonDocument document = hasJsonPathPlaceholder(titleTemplate) || hasJsonPathPlaceholder(messageTemplate)
                    ? responseDocument(history) : JsonDocument.from(Map.of());
            String title = titleTemplate.isBlank()
                    ? text(notificationPayload.get("title")) : renderTemplate(titleTemplate, values, document);
            String message = messageTemplate.isBlank()
                    ? text(notificationPayload.get("body")) : renderTemplate(messageTemplate, values, document);
            if (title.isBlank()) title = text(notificationPayload.get("title"));
            if (message.isBlank()) message = text(notificationPayload.get("body"));
            return new Presentation(title, message, List.of());
        } catch (Exception exception) {
            log.warn("Alert title/message presentation could not be rendered for alertId={}; using legacy templates",
                    alert == null ? null : alert.getId(), exception);
            return null;
        }
    }

    private boolean hasJsonPathPlaceholder(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.equals("data") || token.equals("response")
                    || token.startsWith("data.") || token.startsWith("response.")) return true;
        }
        return false;
    }

    public Presentation build(AlertHistory history, Map<String, Object> notificationPayload) {
        Alert alert = history == null ? null : history.getAlert();
        AlertRule rule = alert == null ? null : alert.getAlertRule();
        if (rule == null || rule.getPresentationConfig() == null || rule.getPresentationConfig().isBlank()) return null;
        try {
            Map<String, Object> config = objectMapper.readValue(rule.getPresentationConfig(), Map.class);
            if (config == null || config.isEmpty() || !supportedVersion(config)) return null;
            Object rawSections = config.get("sections");
            List<?> sections = rawSections instanceof List<?> list ? list : List.of();
            String titleTemplate = text(config.get("titleTemplate"));
            String messageTemplate = text(config.get("messageTemplate"));
            if (sections.isEmpty() && titleTemplate.isBlank() && messageTemplate.isBlank()) return null;

            Map<String, Object> templateValues = templateValues(alert, history, notificationPayload);
            JsonDocument document = sections.isEmpty() ? JsonDocument.from(Map.of()) : responseDocument(history);
            List<Map<String, Object>> renderedSections = new ArrayList<>();
            int renderedSize = 0;
            for (Object rawSection : sections) {
                if (!(rawSection instanceof Map<?, ?> section)) continue;
                try {
                    Map<String, Object> rendered = renderSection(section, document, templateValues);
                    if (rendered == null) continue;
                    int sectionSize = objectMapper.writeValueAsString(rendered).length();
                    if (renderedSize + sectionSize > MAX_PRESENTATION_SIZE) {
                        log.warn("Alert presentation sections exceeded the configured output limit; remaining sections were omitted");
                        break;
                    }
                    renderedSections.add(rendered);
                    renderedSize += sectionSize;
                } catch (Exception sectionException) {
                    log.warn("Skipping presentation section id={} because its source data could not be rendered",
                            section.get("id"), sectionException);
                }
            }
            String title = titleTemplate.isBlank()
                    ? text(notificationPayload.get("title")) : renderTemplate(titleTemplate, templateValues, document);
            String message = messageTemplate.isBlank()
                    ? text(notificationPayload.get("body")) : renderTemplate(messageTemplate, templateValues, document);
            if (title.isBlank()) title = text(notificationPayload.get("title"));
            if (message.isBlank()) message = text(notificationPayload.get("body"));
            return new Presentation(title, message, renderedSections);
        } catch (Exception exception) {
            log.warn("Alert presentation configuration could not be rendered for alertId={}; using legacy message content",
                    alert == null ? null : alert.getId(), exception);
            return null;
        }
    }

    private boolean supportedVersion(Map<String, Object> config) {
        Object version = config.get("version");
        return version == null || "1".equals(String.valueOf(version));
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
        MonitoringResult latest = resultRepository
                .findFirstByExecution_IdOrderByCreatedAtDescIdDesc(execution.getId())
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
        for (String line : source.split("\\R")) {
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
        String title = renderTemplate(text(section.get("title")), templateValues, document);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "KEY_VALUE".equals(type) ? "details" : type.toLowerCase(Locale.ROOT));
        if (!title.isBlank()) result.put("title", title);
        switch (type) {
            case "TEXT" -> result.put("text", renderTemplate(text(section.get("text")), templateValues, document));
            case "KEY_VALUE" -> {
                List<Map<String, String>> fields = new ArrayList<>();
                Object rawFields = section.get("fields");
                if (rawFields instanceof List<?> list) {
                    for (Object rawField : list) {
                        if (!(rawField instanceof Map<?, ?> field)) continue;
                        String path = text(field.get("path"));
                        JsonPathResolver.ResolvedValues resolved = resolve(document, path);
                        if (resolved.found()) {
                            fields.add(Map.of("label", renderTemplate(text(field.get("label")), templateValues, document),
                                    "value", displayValue(observed(resolved.values()))));
                        }
                    }
                }
                if (fields.isEmpty()) return emptyText(section, title);
                result.put("fields", fields);
            }
            case "TABLE" -> {
                Object source = sourceValue(document, text(section.get("sourcePath")));
                List<?> rows = collectionItems(source);
                if (rows == null || rows.isEmpty()) {
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
                List<?> items = collectionItems(source);
                if (items == null && source instanceof Map<?, ?>) items = List.of(source);
                if (items == null || items.isEmpty()) return emptyText(section, title);
                int maxItems = integer(section.get("maxItems"), 20);
                String legacyValuePath = text(section.get("valuePath"));
                Object rawFields = section.get("fields");
                List<Map<String, String>> fields = new ArrayList<>();
                if (rawFields instanceof List<?> configuredFields) {
                    for (Object rawField : configuredFields) {
                        if (rawField instanceof Map<?, ?> field) {
                            String key = text(field.get("key"));
                            if (!key.isBlank()) fields.add(Map.of(
                                    "key", key,
                                    "label", text(field.get("label")).isBlank() ? key : text(field.get("label"))));
                        }
                    }
                }
                List<String> values = new ArrayList<>();
                for (Object item : items.stream().limit(maxItems).toList()) {
                    if (!fields.isEmpty() && item instanceof Map<?, ?>) {
                        List<String> attributes = new ArrayList<>();
                        for (Map<String, String> field : fields) {
                            Object value = resolveRelative(item, field.get("key"));
                            if (value != null) attributes.add(field.get("label") + ": " + displayValue(value));
                        }
                        values.add(String.join(" | ", attributes));
                    } else {
                        Object value = legacyValuePath.isBlank() ? item : resolveRelative(item, legacyValuePath);
                        values.add(displayValue(value));
                    }
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

    private List<?> collectionItems(Object source) {
        if (!(source instanceof List<?> list)) return null;
        List<Object> items = new ArrayList<>();
        flattenItems(list, items);
        return items;
    }

    private void flattenItems(List<?> source, List<Object> target) {
        for (Object item : source) {
            if (item instanceof List<?> nested) flattenItems(nested, target);
            else target.add(item);
        }
    }

    private Object sourceValue(JsonDocument document, String path) {
        JsonPathResolver.ResolvedValues resolved = resolve(document, path);
        if (!resolved.found() || resolved.values().isEmpty()) return null;
        if (resolved.values().size() == 1) return resolved.values().get(0);
        List<Object> flattened = new ArrayList<>();
        for (Object value : resolved.values()) {
            if (value instanceof List<?> list) flattened.addAll(list);
            else flattened.add(value);
        }
        return flattened;
    }

    private JsonPathResolver.ResolvedValues resolve(JsonDocument document, String path) {
        if (path == null || path.isBlank()) return new JsonPathResolver.ResolvedValues(false, List.of(), false);
        return pathResolver.resolve(document, path);
    }

    private Object resolveRelative(Object root, String path) {
        if (path == null || path.isBlank()) return root;
        if ("value".equals(path) && !(root instanceof Map<?, ?>) && !(root instanceof List<?>)) return root;
        List<Object> current = new ArrayList<>();
        current.add(root);
        for (String segment : path.split("\\.")) {
            List<Object> next = new ArrayList<>();
            for (Object candidate : current) {
                if (candidate instanceof Map<?, ?> map && map.containsKey(segment)) {
                    next.add(map.get(segment));
                } else if (candidate instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map && map.containsKey(segment)) next.add(map.get(segment));
                    }
                }
            }
            current = next;
            if (current.isEmpty()) return null;
        }
        return current.size() == 1 ? current.get(0) : current;
    }

    private Object observed(List<Object> values) {
        if (values.size() == 1) return values.get(0);
        return values;
    }

    private String renderTemplate(String template, Map<String, Object> values, JsonDocument document) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            Object replacement = values.get(token);
            if (replacement == null) {
                String candidate = token.startsWith("data.") || token.startsWith("response.")
                        ? token : "data." + token;
                JsonPathResolver.ResolvedValues resolved = resolve(document, candidate);
                if (!resolved.found() && !token.startsWith("data.") && !token.startsWith("response.")) {
                    resolved = resolve(document, "data." + token.replace('_', '.'));
                }
                if (!resolved.found() && !token.startsWith("data.") && !token.startsWith("response.")) {
                    resolved = resolve(document, "response." + token);
                }
                if (resolved.found()) replacement = observed(resolved.values());
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    replacement == null ? "" : displayValue(replacement)));
        }
        matcher.appendTail(output);
        return output.length() > MAX_SECTION_TEXT ? output.substring(0, MAX_SECTION_TEXT) : output.toString();
    }

    private String displayValue(Object value) {
        if (value == null) return "null";
        String rendered;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try { rendered = objectMapper.writeValueAsString(value); }
            catch (Exception ignored) { rendered = String.valueOf(value); }
        } else {
            rendered = String.valueOf(value);
        }
        return rendered.length() > MAX_DISPLAY_VALUE
                ? rendered.substring(0, MAX_DISPLAY_VALUE) + "… (truncated)" : rendered;
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
