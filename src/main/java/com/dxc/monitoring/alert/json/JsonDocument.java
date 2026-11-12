package com.dxc.monitoring.alert.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable view of a monitoring response with explicit platform and payload
 * namespaces. The original response is always retained without rewriting it.
 */
public final class JsonDocument {
    private static final int MAX_DOCUMENT_DEPTH = 64;
    private static final int MAX_DOCUMENT_NODES = 50_000;
    private static final int MAX_STRING_LENGTH = 1_000_000;

    private final Object response;
    private final Object data;

    private JsonDocument(Object response, Object data) {
        this.response = response;
        this.data = data;
    }

    public static JsonDocument from(Object response) {
        Object normalizedResponse = immutableValue(response, 0, new int[] {0});
        Object payload = normalizedResponse instanceof Map<?, ?> map && map.containsKey("data")
                ? map.get("data")
                : normalizedResponse;
        return new JsonDocument(normalizedResponse, payload);
    }

    public Object response() {
        return response;
    }

    public Object data() {
        return data;
    }

    /**
     * Resolves only the two documented roots. There is deliberately no
     * cross-namespace fallback when a path is absent.
     */
    Object namespaceRoot(String namespace) {
        return switch (namespace) {
            case "response" -> response;
            case "data" -> data;
            default -> throw new IllegalArgumentException(
                    "JSON paths must start with 'response' or 'data'.");
        };
    }

    private static Object immutableValue(Object value, int depth, int[] nodes) {
        if (depth > MAX_DOCUMENT_DEPTH) {
            throw new IllegalArgumentException("Monitoring response exceeds the maximum JSON nesting depth.");
        }
        if (++nodes[0] > MAX_DOCUMENT_NODES) {
            throw new IllegalArgumentException("Monitoring response exceeds the maximum JSON node count.");
        }
        if (value instanceof String text && text.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Monitoring response contains a string that exceeds the maximum length.");
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), immutableValue(item, depth + 1, nodes)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.List<?> list) {
            java.util.List<Object> copy = new java.util.ArrayList<>(list.size());
            for (Object item : list) copy.add(immutableValue(item, depth + 1, nodes));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
