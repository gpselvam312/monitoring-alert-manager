package com.dxc.monitoring.alert.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable view of a monitoring response with explicit platform and payload
 * namespaces. The original response is always retained without rewriting it.
 */
public final class JsonDocument {
    private final Object response;
    private final Object data;

    private JsonDocument(Object response, Object data) {
        this.response = response;
        this.data = data;
    }

    public static JsonDocument from(Object response) {
        Object normalizedResponse = immutableValue(response);
        Object payload = normalizedResponse instanceof Map<?, ?> map && map.containsKey("data")
                ? map.get("data")
                : normalizedResponse;
        return new JsonDocument(normalizedResponse, immutableValue(payload));
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

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), immutableValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(JsonDocument::immutableValue).toList();
        }
        return value;
    }
}
