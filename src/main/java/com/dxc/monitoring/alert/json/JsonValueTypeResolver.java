package com.dxc.monitoring.alert.json;

import java.util.LinkedHashSet;
import java.util.Set;

/** Infers the observed JSON value type without coercing values. */
public final class JsonValueTypeResolver {

    public ValueType resolve(JsonPathResolver.ResolvedValues resolved) {
        if (resolved == null || !resolved.found() || resolved.values().isEmpty()) {
            return ValueType.UNKNOWN;
        }

        Set<ValueType> types = new LinkedHashSet<>();
        for (Object value : resolved.values()) {
            types.add(typeOf(value));
        }
        if (types.size() > 1) return ValueType.MIXED;
        return types.iterator().next();
    }

    public ValueType typeOf(Object value) {
        if (value == null) return ValueType.NULL;
        if (value instanceof String || value instanceof Character) return ValueType.STRING;
        if (value instanceof Number) return ValueType.NUMBER;
        if (value instanceof Boolean) return ValueType.BOOLEAN;
        if (value instanceof java.util.List<?>) return ValueType.ARRAY;
        if (value instanceof java.util.Map<?, ?>) return ValueType.OBJECT;
        return ValueType.UNKNOWN;
    }

    public enum ValueType {
        STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY, MIXED, UNKNOWN
    }
}
