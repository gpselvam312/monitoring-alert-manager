package com.dxc.monitoring.alert.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Save-time validation for channel-neutral notification presentation documents. */
public final class PresentationConfigurationValidator {
    private static final int MAX_SECTIONS = 20;
    private static final int MAX_FIELDS = 50;
    private static final int MAX_COLUMNS = 20;
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    public Map<String, Object> normalize(Object raw) {
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Presentation configuration must be a JSON object.");
        }
        Map<String, Object> config = stringMap(source);
        Object version = config.getOrDefault("version", 1);
        if (!"1".equals(String.valueOf(version))) {
            throw new IllegalArgumentException("Unsupported presentation configuration version.");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("version", 1);
        copyTemplate(config, normalized, "titleTemplate", 500);
        copyTemplate(config, normalized, "messageTemplate", MAX_TEXT_LENGTH);

        Object rawSections = config.get("sections");
        if (rawSections != null) {
            if (!(rawSections instanceof List<?> sections) || sections.size() > MAX_SECTIONS) {
                throw new IllegalArgumentException("Presentation configuration supports up to 20 sections.");
            }
            List<Map<String, Object>> result = new ArrayList<>();
            Set<String> ids = new java.util.HashSet<>();
            int index = 0;
            for (Object rawSection : sections) {
                Map<String, Object> section = normalizeSection(rawSection, ++index);
                String id = String.valueOf(section.get("id"));
                if (!ids.add(id)) throw new IllegalArgumentException("Presentation section identifiers must be unique.");
                result.add(section);
            }
            normalized.put("sections", result);
        } else {
            normalized.put("sections", List.of());
        }
        return normalized;
    }

    private Map<String, Object> normalizeSection(Object raw, int index) {
        if (!(raw instanceof Map<?, ?> source)) throw new IllegalArgumentException("Each presentation section must be an object.");
        Map<String, Object> section = stringMap(source);
        String type = required(section, "type").toUpperCase(Locale.ROOT);
        if (!Set.of("TEXT", "KEY_VALUE", "TABLE", "LIST", "JSON").contains(type)) {
            throw new IllegalArgumentException("Unsupported presentation section type: " + type);
        }
        String id = optionalText(section.get("id"), 80);
        if (id.isBlank()) id = "section-" + index;
        String title = optionalText(section.get("title"), 150);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("type", type);
        if (!title.isBlank()) result.put("title", title);
        switch (type) {
            case "TEXT" -> {
                String text = optionalText(section.getOrDefault("text", section.get("template")), MAX_TEXT_LENGTH);
                if (text.isBlank()) throw new IllegalArgumentException("TEXT sections require 'text' or 'template'.");
                result.put("text", text);
            }
            case "KEY_VALUE" -> {
                Object rawFields = section.get("fields");
                if (!(rawFields instanceof List<?> fields) || fields.isEmpty() || fields.size() > MAX_FIELDS) {
                    throw new IllegalArgumentException("KEY_VALUE sections require between 1 and 50 fields.");
                }
                List<Map<String, String>> normalizedFields = new ArrayList<>();
                for (Object rawField : fields) {
                    if (!(rawField instanceof Map<?, ?> field)) throw new IllegalArgumentException("Each key/value field must be an object.");
                    String label = optionalText(field.get("label"), 100);
                    String path = documentPath(field.get("path"));
                    if (label.isBlank()) label = path;
                    normalizedFields.add(Map.of("label", label, "path", path));
                }
                result.put("fields", normalizedFields);
            }
            case "TABLE" -> {
                result.put("sourcePath", documentPath(section.get("sourcePath")));
                Object rawColumns = section.get("columns");
                if (!(rawColumns instanceof List<?> columns) || columns.isEmpty() || columns.size() > MAX_COLUMNS) {
                    throw new IllegalArgumentException("TABLE sections require between 1 and 20 columns.");
                }
                List<Map<String, String>> normalizedColumns = new ArrayList<>();
                for (Object rawColumn : columns) {
                    if (!(rawColumn instanceof Map<?, ?> column)) throw new IllegalArgumentException("Each table column must be an object.");
                    String key = relativePath(column.get("key"));
                    String label = optionalText(column.get("label"), 100);
                    if (label.isBlank()) label = key;
                    normalizedColumns.add(Map.of("key", key, "label", label));
                }
                result.put("columns", normalizedColumns);
                result.put("maxRows", boundedInteger(section.get("maxRows"), 20, 1, 100, "maxRows"));
                copyOptionalText(section, result, "emptyText", 300);
            }
            case "LIST" -> {
                result.put("sourcePath", path(section.get("sourcePath")));
                if (section.containsKey("valuePath")) result.put("valuePath", relativePath(section.get("valuePath")));
                result.put("maxItems", boundedInteger(section.get("maxItems"), 20, 1, 100, "maxItems"));
                copyOptionalText(section, result, "emptyText", 300);
            }
            case "JSON" -> {
                result.put("sourcePath", path(section.get("sourcePath")));
                result.put("maxLength", boundedInteger(section.get("maxLength"), 4000, 100, 10_000, "maxLength"));
            }
            default -> throw new IllegalArgumentException("Unsupported presentation section type.");
        }
        return result;
    }

    private void copyTemplate(Map<String, Object> source, Map<String, Object> target, String key, int maxLength) {
        if (!source.containsKey(key)) return;
        String value = optionalText(source.get(key), maxLength);
        if (!value.isBlank()) target.put(key, value);
    }

    private void copyOptionalText(Map<String, Object> source, Map<String, Object> target, String key, int maxLength) {
        if (!source.containsKey(key)) return;
        String value = optionalText(source.get(key), maxLength);
        if (!value.isBlank()) target.put(key, value);
    }

    private String documentPath(Object raw) {
        String value = relativePath(raw);
        if (!(value.equals("response") || value.equals("data")
                || value.startsWith("response.") || value.startsWith("data."))) {
            throw new IllegalArgumentException("Presentation source paths must start with response. or data.");
        }
        return value;
    }

    private String relativePath(Object raw) {
        String value = optionalText(raw, 255);
        if (value.isBlank() || !PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("Presentation paths must use dot notation and cannot exceed 255 characters.");
        }
        return value;
    }

    private String optionalText(Object raw, int maxLength) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.length() > maxLength) throw new IllegalArgumentException("Presentation text exceeds the maximum length.");
        return value;
    }

    private int boundedInteger(Object raw, int fallback, int min, int max, String label) {
        if (raw == null) return fallback;
        try {
            int value = Integer.parseInt(String.valueOf(raw));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Presentation " + label + " must be between " + min + " and " + max + ".");
        }
    }

    private String required(Map<String, Object> map, String key) {
        String value = optionalText(map.get(key), 100);
        if (value.isBlank()) throw new IllegalArgumentException("Presentation configuration requires '" + key + "'.");
        return value;
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
