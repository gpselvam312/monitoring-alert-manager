package com.dxc.monitoring.alert.condition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Save-time normalization and structural validation for condition trees. */
public final class ConditionConfigurationValidator {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 100;
    private static final Pattern FIELD_PATH = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");
    private static final Set<String> OPERATORS = Set.of(
            "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
            "GT", "GTE", "LT", "LTE", "EQUALS", "EQ", "NOT_EQUALS", "NE",
            "EQUALS_IGNORE_CASE", "EQ_IGNORE_CASE", "CONTAINS", "NOT_CONTAINS",
            "STARTS_WITH", "ENDS_WITH", "REGEX", "EXISTS", "NOT_EXISTS",
            "IS_TRUE", "IS_FALSE", "IS_NULL", "IS_NOT_NULL");
    private static final Set<String> THRESHOLD_FREE = Set.of(
            "EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE", "IS_NULL", "IS_NOT_NULL");
    private static final Set<String> COUNT_OPERATORS = Set.of(
            "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
            "GT", "GTE", "LT", "LTE", "EQUALS", "EQ", "NOT_EQUALS", "NE");

    public Map<String, Object> normalize(Object raw) {
        int[] count = {0};
        return normalizeNode(raw, 0, count);
    }

    private Map<String, Object> normalizeNode(Object raw, int depth, int[] count) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Condition groups cannot be nested more than 8 levels deep.");
        if (!(raw instanceof Map<?, ?> source)) throw new IllegalArgumentException("Each condition must be a valid condition or group.");
        if (++count[0] > MAX_NODES) throw new IllegalArgumentException("An alert condition tree cannot contain more than 100 nodes.");
        Map<String, Object> node = stringMap(source);
        String type = string(node, "type", "CONDITION").toUpperCase(Locale.ROOT);

        return switch (type) {
            case "GROUP" -> normalizeGroup(node, depth, count);
            case "CONDITION" -> normalizeCondition(node);
            case "FIELD_COMPARISON" -> normalizeFieldComparison(node);
            case "COLLECTION" -> normalizeCollection(node, depth, count);
            case "AGGREGATE" -> normalizeAggregate(node, depth, count);
            case "COUNT_TOTAL" -> normalizeCountTotal(node);
            case "COUNT_MATCHING" -> normalizeCountMatching(node);
            default -> throw new IllegalArgumentException("Unsupported condition node type: " + type);
        };
    }

    private Map<String, Object> normalizeGroup(Map<String, Object> node, int depth, int[] count) {
        String operator = required(node, "operator").toUpperCase(Locale.ROOT);
        if (!Set.of("AND", "OR").contains(operator)) {
            throw new IllegalArgumentException("Choose AND or OR for each condition group.");
        }
        Object rawChildren = node.get("children");
        if (!(rawChildren instanceof List<?> children) || children.size() < 2) {
            throw new IllegalArgumentException("Each condition group must contain at least two children.");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object child : children) normalized.add(normalizeNode(child, depth + 1, count));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "GROUP");
        result.put("operator", operator);
        result.put("children", normalized);
        return result;
    }

    private Map<String, Object> normalizeCondition(Map<String, Object> node) {
        String path = path(node, "fieldPath");
        String operator = operator(node, "operator");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "CONDITION");
        result.put("fieldPath", path);
        result.put("operator", operator);
        if (!THRESHOLD_FREE.contains(operator)) result.put("threshold", threshold(node.get("threshold") == null ? node.get("value") : node.get("threshold"), operator));
        return result;
    }

    private Map<String, Object> normalizeFieldComparison(Map<String, Object> node) {
        String left = path(node, "leftPath");
        String right = path(node, "rightPath");
        String operator = operator(node, "operator");
        if (THRESHOLD_FREE.contains(operator)) {
            throw new IllegalArgumentException("Field-to-field comparison requires a comparison operator.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FIELD_COMPARISON");
        result.put("leftPath", left);
        result.put("rightPath", right);
        result.put("operator", operator);
        return result;
    }

    private Map<String, Object> normalizeCollection(Map<String, Object> node, int depth, int[] count) {
        String collectionPath = path(node, "collectionPath");
        String quantifier = required(node, "quantifier").toUpperCase(Locale.ROOT);
        if (!Set.of("ANY", "ALL", "NONE").contains(quantifier)) {
            throw new IllegalArgumentException("Collection quantifier must be ANY, ALL, or NONE.");
        }
        Map<String, Object> predicate = normalizeNode(node.get("condition"), depth + 1, count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "COLLECTION");
        result.put("collectionPath", collectionPath);
        result.put("quantifier", quantifier);
        result.put("condition", predicate);
        if (node.containsKey("emptyMatches")) result.put("emptyMatches", Boolean.TRUE.equals(node.get("emptyMatches")));
        return result;
    }

    private Map<String, Object> normalizeAggregate(Map<String, Object> node, int depth, int[] count) {
        String collectionPath = path(node, "collectionPath");
        String operation = required(node, "operation").toUpperCase(Locale.ROOT);
        if (!Set.of("COUNT", "SUM", "MIN", "MAX").contains(operation)) {
            throw new IllegalArgumentException("Aggregate operation must be COUNT, SUM, MIN, or MAX.");
        }
        String operator = operator(node, "operator");
        if (THRESHOLD_FREE.contains(operator)) {
            throw new IllegalArgumentException("Aggregate comparison requires a threshold operator.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "AGGREGATE");
        result.put("collectionPath", collectionPath);
        result.put("operation", operation);
        if (!operation.equals("COUNT")) result.put("valuePath", path(node, "valuePath"));
        result.put("operator", operator);
        result.put("threshold", threshold(node.get("threshold"), operator));
        if (node.containsKey("filter")) result.put("filter", normalizeNode(node.get("filter"), depth + 1, count));
        return result;
    }

    private Map<String, Object> normalizeCountTotal(Map<String, Object> node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "COUNT_TOTAL");
        result.put("collectionPath", path(node, "collectionPath"));
        normalizeCountComparison(node, result);
        return result;
    }

    private Map<String, Object> normalizeCountMatching(Map<String, Object> node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "COUNT_MATCHING");
        result.put("collectionPath", path(node, "collectionPath"));
        result.put("fieldPath", path(node, "fieldPath"));
        String fieldOperator = operator(node, "operator");
        result.put("operator", fieldOperator);
        if (!THRESHOLD_FREE.contains(fieldOperator)) {
            result.put("threshold", threshold(node.get("threshold") == null ? node.get("value") : node.get("threshold"), fieldOperator));
        }
        normalizeCountComparison(node, result);
        return result;
    }

    private void normalizeCountComparison(Map<String, Object> source, Map<String, Object> target) {
        String operator = required(source, "countOperator").toUpperCase(Locale.ROOT);
        if (!COUNT_OPERATORS.contains(operator)) throw new IllegalArgumentException("Choose a supported count comparison operator.");
        Object rawThreshold = source.get("countThreshold");
        if (rawThreshold == null) throw new IllegalArgumentException("Count threshold is required.");
        BigDecimal threshold;
        try {
            threshold = new BigDecimal(String.valueOf(rawThreshold));
            if (threshold.signum() < 0 || threshold.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Count threshold must be a non-negative whole number.");
        }
        target.put("countOperator", operator);
        target.put("countThreshold", threshold.longValueExact());
    }

    private String path(Map<String, Object> node, String key) {
        String value = required(node, key);
        if (value.length() > 255 || !FIELD_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("Condition field paths must use dot notation and cannot exceed 255 characters.");
        }
        return value;
    }

    private String operator(Map<String, Object> node, String key) {
        String value = required(node, key).toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(value)) throw new IllegalArgumentException("Choose a supported condition operator.");
        return value;
    }

    private Object threshold(Object raw, String operator) {
        if (raw == null || String.valueOf(raw).isBlank()) throw new IllegalArgumentException("A threshold/value is required for this condition.");
        if (Set.of("GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
                "GT", "GTE", "LT", "LTE").contains(operator)) {
            try {
                return new BigDecimal(String.valueOf(raw));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Numeric comparison thresholds must be valid numbers.");
            }
        }
        String value = String.valueOf(raw).trim();
        if (value.length() > 500) throw new IllegalArgumentException("Condition value is too long.");
        return value;
    }

    private String required(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Condition configuration requires '" + key + "'.");
        return String.valueOf(value).trim();
    }

    private String string(Map<String, Object> node, String key, String fallback) {
        Object value = node.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
