package com.dxc.monitoring.alert.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dxc.monitoring.alert.json.JsonDocument;
import com.dxc.monitoring.alert.json.JsonPathResolver;

/**
 * Immutable evaluation scope. Namespaced paths always resolve against the
 * original document; unqualified paths retain the legacy rule view or resolve
 * relative to the current collection element.
 */
public final class ConditionEvaluationContext {
    private final JsonDocument document;
    private final Map<String, Object> legacyValues;
    private final Map<String, Object> currentElement;
    private final JsonPathResolver pathResolver;

    public ConditionEvaluationContext(JsonDocument document, Map<String, Object> legacyValues) {
        this(document, legacyValues, null, new JsonPathResolver());
    }

    private ConditionEvaluationContext(JsonDocument document, Map<String, Object> legacyValues,
            Map<String, Object> currentElement, JsonPathResolver pathResolver) {
        this.document = document;
        this.legacyValues = legacyValues == null ? Map.of() : legacyValues;
        this.currentElement = currentElement;
        this.pathResolver = pathResolver;
    }

    public ConditionEvaluationContext forElement(Map<String, Object> element) {
        return new ConditionEvaluationContext(document, legacyValues, element, pathResolver);
    }

    public ResolvedPath resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Condition field path is required.");
        }
        if (path.equals("response") || path.equals("data")
                || path.startsWith("response.") || path.startsWith("data.")) {
            JsonPathResolver.ResolvedValues resolved = pathResolver.resolve(document, path);
            return new ResolvedPath(resolved.found(), resolved.values(), resolved.emptyCollectionMatched());
        }
        Object root = currentElement == null ? legacyValues : currentElement;
        return resolveRelative(root, path);
    }

    public Object metadata(String key) {
        return legacyValues.get(key);
    }

    private ResolvedPath resolveRelative(Object root, String path) {
        if (path.length() > JsonPathResolver.MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Condition field path exceeds the maximum length.");
        }
        String[] segments = path.split("\\.", -1);
        if (segments.length > JsonPathResolver.MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("Condition field path exceeds the maximum depth.");
        }
        for (String segment : segments) {
            if (segment.isBlank() || segment.contains("[") || segment.contains("]")) {
                throw new IllegalArgumentException("Invalid condition field path: " + path);
            }
        }
        List<Object> values = new ArrayList<>();
        boolean[] found = {false};
        walk(root, segments, 0, values, found, new int[] {0});
        return new ResolvedPath(found[0], values, false);
    }

    private void walk(Object current, String[] segments, int position, List<Object> values,
            boolean[] found, int[] visited) {
        if (++visited[0] > JsonPathResolver.DEFAULT_MAX_VISITED_VALUES) {
            throw new IllegalArgumentException("Condition field traversal exceeded the configured value limit.");
        }
        if (position == segments.length) {
            found[0] = true;
            values.add(current);
            return;
        }
        if (current instanceof Map<?, ?> map) {
            if (map.containsKey(segments[position])) {
                walk(map.get(segments[position]), segments, position + 1, values, found, visited);
            }
            return;
        }
        if (current instanceof List<?> list) {
            String segment = segments[position];
            if (segment.matches("0|[1-9][0-9]{0,8}")) {
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException exception) {
                    return;
                }
                if (index >= 0 && index < list.size()) {
                    walk(list.get(index), segments, position + 1, values, found, visited);
                }
                return;
            }
            for (Object item : list) {
                walk(item, segments, position, values, found, visited);
            }
        }
    }

    public record ResolvedPath(boolean found, List<Object> values, boolean emptyCollectionMatched) {
        public ResolvedPath {
            values = java.util.Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
