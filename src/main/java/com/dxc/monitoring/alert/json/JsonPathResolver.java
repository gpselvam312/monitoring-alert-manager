package com.dxc.monitoring.alert.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves namespace-qualified paths and transparently traverses arrays.
 *
 * For example, data.groups.items.totalLag returns totalLag from every item
 * in the items array. Numeric path segments can still address an explicit
 * array index when required.
 */
public final class JsonPathResolver {
    public static final int MAX_PATH_LENGTH = 512;
    public static final int MAX_PATH_DEPTH = 32;
    public static final int DEFAULT_MAX_VISITED_VALUES = 10_000;

    private final int maxVisitedValues;

    public JsonPathResolver() {
        this(DEFAULT_MAX_VISITED_VALUES);
    }

    public JsonPathResolver(int maxVisitedValues) {
        if (maxVisitedValues < 1) {
            throw new IllegalArgumentException("maxVisitedValues must be positive.");
        }
        this.maxVisitedValues = maxVisitedValues;
    }

    public ResolvedValues resolve(JsonDocument document, String path) {
        if (document == null) throw new IllegalArgumentException("JSON document is required.");
        List<String> segments = parse(path);
        Object root = document.namespaceRoot(segments.get(0));
        if (segments.size() == 1) {
            List<Object> rootValue = new ArrayList<>();
            rootValue.add(root);
            return new ResolvedValues(true, rootValue, root instanceof List<?> list && list.isEmpty());
        }

        Traversal traversal = new Traversal(maxVisitedValues);
        walk(root, segments, 1, traversal);
        return new ResolvedValues(!traversal.values.isEmpty(), traversal.values,
                traversal.emptyCollectionMatched);
    }

    private List<String> parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("JSON path is required.");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("JSON path exceeds the maximum length.");
        }
        String[] raw = path.split("\\.", -1);
        if (raw.length > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("JSON path exceeds the maximum depth.");
        }
        List<String> segments = new ArrayList<>(raw.length);
        for (String segment : raw) {
            if (segment.isBlank() || segment.contains("[") || segment.contains("]")) {
                throw new IllegalArgumentException("Invalid JSON path segment: " + segment);
            }
            segments.add(segment);
        }
        if (!segments.get(0).equals("response") && !segments.get(0).equals("data")) {
            throw new IllegalArgumentException("JSON paths must start with 'response' or 'data'.");
        }
        return segments;
    }

    private void walk(Object current, List<String> segments, int position, Traversal traversal) {
        if (traversal.visited++ >= traversal.limit) {
            throw new IllegalArgumentException("JSON path traversal exceeded the configured value limit.");
        }
        if (position == segments.size()) {
            if (current instanceof List<?> list && list.isEmpty()) {
                traversal.emptyCollectionMatched = true;
            }
            traversal.values.add(current);
            return;
        }

        String segment = segments.get(position);
        if (current instanceof Map<?, ?> map) {
            if (map.containsKey(segment)) {
                walk(map.get(segment), segments, position + 1, traversal);
            }
            return;
        }
        if (current instanceof List<?> list) {
            Integer index = numericIndex(segment);
            if (index != null) {
                if (index >= 0 && index < list.size()) {
                    walk(list.get(index), segments, position + 1, traversal);
                }
                return;
            }
            if (list.isEmpty()) {
                traversal.emptyCollectionMatched = true;
                return;
            }
            for (Object item : list) {
                walk(item, segments, position, traversal);
            }
        }
    }

    private Integer numericIndex(String segment) {
        if (!segment.matches("0|[1-9][0-9]{0,8}")) return null;
        try {
            return Integer.valueOf(segment);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class Traversal {
        private final int limit;
        private final List<Object> values = new ArrayList<>();
        private int visited;
        private boolean emptyCollectionMatched;

        private Traversal(int limit) {
            this.limit = limit;
        }
    }

    public record ResolvedValues(boolean found, List<Object> values, boolean emptyCollectionMatched) {
        public ResolvedValues {
            values = java.util.Collections.unmodifiableList(new ArrayList<>(values));
        }

        public boolean hasSingleValue() {
            return found && values.size() == 1;
        }

        public Object singleValue() {
            if (!hasSingleValue()) {
                throw new IllegalStateException("The path does not resolve to exactly one value.");
            }
            return values.get(0);
        }
    }
}
