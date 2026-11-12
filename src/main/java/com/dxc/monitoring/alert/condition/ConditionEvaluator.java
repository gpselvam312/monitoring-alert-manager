package com.dxc.monitoring.alert.condition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Technology-neutral condition tree evaluator. It has no persistence,
 * lifecycle, notification, or monitoring-provider dependencies.
 */
@Component
public class ConditionEvaluator {
    private static final int MAX_TREE_DEPTH = 8;
    private static final int MAX_TREE_NODES = 100;
    private static final int MAX_REGEX_LENGTH = 256;
    private static final int MAX_EXPLANATION_ITEMS = 100;

    public ConditionEvaluationResult evaluate(Map<String, Object> condition,
            ConditionEvaluationContext context) {
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("Alert condition configuration is required.");
        }
        int[] nodes = {0};
        return evaluateNode(condition, context, 0, nodes);
    }

    private ConditionEvaluationResult evaluateNode(Map<String, Object> node,
            ConditionEvaluationContext context, int depth, int[] nodes) {
        if (depth > MAX_TREE_DEPTH) {
            throw new IllegalArgumentException("Condition tree exceeds the maximum nesting depth.");
        }
        if (++nodes[0] > MAX_TREE_NODES) {
            throw new IllegalArgumentException("Condition tree exceeds the maximum node count.");
        }

        String type = string(node, "type", "CONDITION").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "CONDITION" -> evaluateScalarCondition(node, context);
            case "GROUP" -> evaluateGroup(node, context, depth, nodes);
            case "FIELD_COMPARISON" -> evaluateFieldComparison(node, context);
            case "COLLECTION" -> evaluateCollection(node, context, depth, nodes);
            case "AGGREGATE" -> evaluateAggregate(node, context, depth, nodes);
            case "COUNT_TOTAL" -> evaluateLegacyTotalCount(node, context);
            case "COUNT_MATCHING" -> evaluateLegacyMatchingCount(node, context);
            default -> throw new IllegalArgumentException("Unsupported condition node type: " + type);
        };
    }

    private ConditionEvaluationResult evaluateScalarCondition(Map<String, Object> node,
            ConditionEvaluationContext context) {
        String path = required(node, "fieldPath");
        String operator = required(node, "operator").toUpperCase(Locale.ROOT);
        enforceExecutionGuard(path, context);
        ConditionEvaluationContext.ResolvedPath resolved = context.resolve(path);
        Object expected = node.containsKey("threshold") ? node.get("threshold") : node.get("value");
        boolean matched;

        switch (operator) {
            case "EXISTS" -> matched = resolved.found();
            case "NOT_EXISTS" -> matched = !resolved.found();
            case "IS_NULL" -> matched = resolved.found() && resolved.values().stream().anyMatch(value -> value == null);
            case "IS_NOT_NULL" -> matched = resolved.found() && resolved.values().stream().anyMatch(value -> value != null);
            default -> {
                if (!resolved.found()) {
                    throw new IllegalArgumentException("Configured field is missing from the monitoring result: " + path);
                }
                matched = evaluateAny(resolved.values(), operator, expected);
            }
        }

        Object observed = observed(resolved.values(), resolved.found());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", "CONDITION");
        details.put("fieldPath", path);
        details.put("operator", operator);
        details.put("resolved", resolved.found());
        details.put("observedValue", observed);
        details.put("matched", matched);
        return new ConditionEvaluationResult(matched, observed, details);
    }

    private ConditionEvaluationResult evaluateGroup(Map<String, Object> node,
            ConditionEvaluationContext context, int depth, int[] nodes) {
        String operator = required(node, "operator").toUpperCase(Locale.ROOT);
        if (!operator.equals("AND") && !operator.equals("OR")) {
            throw new IllegalArgumentException("Condition group operator must be AND or OR.");
        }
        Object raw = node.get("children");
        if (!(raw instanceof List<?> children) || children.isEmpty()) {
            throw new IllegalArgumentException("A condition group must contain at least one child.");
        }
        boolean matched = operator.equals("AND");
        List<Map<String, Object>> childDetails = new ArrayList<>();
        for (Object child : children) {
            if (!(child instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Each condition group child must be an object.");
            }
            ConditionEvaluationResult result = evaluateNode(stringMap(map), context, depth + 1, nodes);
            childDetails.add(result.details());
            matched = operator.equals("AND") ? matched && result.matched() : matched || result.matched();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", "GROUP");
        details.put("operator", operator);
        details.put("children", childDetails);
        details.put("matched", matched);
        return new ConditionEvaluationResult(matched, childDetails, details);
    }

    private ConditionEvaluationResult evaluateFieldComparison(Map<String, Object> node,
            ConditionEvaluationContext context) {
        String leftPath = required(node, "leftPath");
        String rightPath = required(node, "rightPath");
        enforceExecutionGuard(leftPath, context);
        enforceExecutionGuard(rightPath, context);
        String operator = required(node, "operator").toUpperCase(Locale.ROOT);
        ConditionEvaluationContext.ResolvedPath left = context.resolve(leftPath);
        ConditionEvaluationContext.ResolvedPath right = context.resolve(rightPath);
        if (!left.found() || !right.found()) {
            throw new IllegalArgumentException("Both fields in a field comparison must exist.");
        }
        if (left.values().size() != right.values().size()) {
            throw new IllegalArgumentException("Field comparison paths must resolve to the same number of values.");
        }
        boolean matched = false;
        for (int i = 0; i < left.values().size(); i++) {
            if (evaluateScalarOperator(operator, left.values().get(i), right.values().get(i))) {
                matched = true;
                break;
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", "FIELD_COMPARISON");
        details.put("leftPath", leftPath);
        details.put("rightPath", rightPath);
        details.put("operator", operator);
        details.put("leftValue", observed(left.values(), true));
        details.put("rightValue", observed(right.values(), true));
        details.put("matched", matched);
        return new ConditionEvaluationResult(matched, details, details);
    }

    private ConditionEvaluationResult evaluateCollection(Map<String, Object> node,
            ConditionEvaluationContext context, int depth, int[] nodes) {
        String path = required(node, "collectionPath");
        enforceExecutionGuard(path, context);
        String quantifier = required(node, "quantifier").toUpperCase(Locale.ROOT);
        if (!List.of("ANY", "ALL", "NONE").contains(quantifier)) {
            throw new IllegalArgumentException("Collection quantifier must be ANY, ALL, or NONE.");
        }
        ConditionEvaluationContext.ResolvedPath resolved = context.resolve(path);
        if (!resolved.found() || resolved.values().size() != 1
                || !(resolved.values().get(0) instanceof List<?> records)) {
            throw new IllegalArgumentException("Collection path must resolve to one array: " + path);
        }
        Object rawPredicate = node.get("condition");
        if (!(rawPredicate instanceof Map<?, ?> predicateMap)) {
            throw new IllegalArgumentException("Collection node requires a condition object.");
        }
        if (records.isEmpty()) {
            boolean emptyMatches = Boolean.TRUE.equals(node.get("emptyMatches"));
            boolean emptyResult = quantifier.equals("ALL") && emptyMatches || quantifier.equals("NONE");
            Map<String, Object> emptyDetails = Map.of("nodeType", "COLLECTION", "collectionPath", path,
                    "quantifier", quantifier, "recordCount", 0, "matched", emptyResult);
            return new ConditionEvaluationResult(emptyResult, 0, emptyDetails);
        }

        int matchedCount = 0;
        List<Map<String, Object>> itemResults = new ArrayList<>();
        for (Object record : records) {
            ConditionEvaluationResult result = evaluateNode(stringMap(predicateMap),
                    context.forElement(record), depth + 1, nodes);
            if (result.matched()) matchedCount++;
            if (itemResults.size() < MAX_EXPLANATION_ITEMS) itemResults.add(result.details());
        }
        boolean matched = switch (quantifier) {
            case "ANY" -> matchedCount > 0;
            case "ALL" -> matchedCount == records.size();
            case "NONE" -> matchedCount == 0;
            default -> false;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", "COLLECTION");
        details.put("collectionPath", path);
        details.put("quantifier", quantifier);
        details.put("recordCount", records.size());
        details.put("matchedCount", matchedCount);
        details.put("items", itemResults);
        details.put("itemsTruncated", records.size() > MAX_EXPLANATION_ITEMS);
        details.put("matched", matched);
        return new ConditionEvaluationResult(matched, Map.of("matchedCount", matchedCount,
                "recordCount", records.size()), details);
    }

    private ConditionEvaluationResult evaluateAggregate(Map<String, Object> node,
            ConditionEvaluationContext context, int depth, int[] nodes) {
        String path = required(node, "collectionPath");
        enforceExecutionGuard(path, context);
        String operation = required(node, "operation").toUpperCase(Locale.ROOT);
        if (!List.of("COUNT", "SUM", "MIN", "MAX").contains(operation)) {
            throw new IllegalArgumentException("Aggregate operation must be COUNT, SUM, MIN, or MAX.");
        }
        ConditionEvaluationContext.ResolvedPath resolved = context.resolve(path);
        if (!resolved.found() || resolved.values().size() != 1
                || !(resolved.values().get(0) instanceof List<?> records)) {
            throw new IllegalArgumentException("Aggregate collection path must resolve to one array: " + path);
        }

        List<Object> included = new ArrayList<>();
        Object rawFilter = node.get("filter");
        for (Object record : records) {
            if (rawFilter instanceof Map<?, ?> filter) {
                ConditionEvaluationResult filterResult = evaluateNode(stringMap(filter),
                        context.forElement(record), depth + 1, nodes);
                if (!filterResult.matched()) continue;
            }
            included.add(record);
        }

        Object aggregate;
        if (operation.equals("COUNT")) {
            aggregate = BigDecimal.valueOf(included.size());
        } else {
            String valuePath = required(node, "valuePath");
            enforceExecutionGuard(valuePath, context);
            List<BigDecimal> numbers = new ArrayList<>();
            for (Object record : included) {
                ConditionEvaluationContext itemContext = context.forElement(record);
                ConditionEvaluationContext.ResolvedPath values = itemContext.resolve(valuePath);
                if (!values.found() || values.values().isEmpty()) {
                    throw new IllegalArgumentException("Aggregate " + operation + " is missing values at " + valuePath + ".");
                }
                for (Object value : values.values()) {
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException("Aggregate " + operation + " requires numeric values at " + valuePath + ".");
                    }
                    numbers.add(new BigDecimal(String.valueOf(value)));
                }
            }
            if (numbers.isEmpty() && !included.isEmpty()) {
                throw new IllegalArgumentException("Aggregate " + operation + " found no numeric values.");
            }
            aggregate = switch (operation) {
                case "SUM" -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                case "MIN" -> numbers.stream().min(BigDecimal::compareTo).orElse(null);
                case "MAX" -> numbers.stream().max(BigDecimal::compareTo).orElse(null);
                default -> throw new IllegalArgumentException("Unsupported aggregate operation.");
            };
        }

        String comparison = required(node, "operator").toUpperCase(Locale.ROOT);
        Object threshold = node.get("threshold");
        boolean matched = aggregate != null && evaluateScalarOperator(comparison, aggregate, threshold);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", "AGGREGATE");
        details.put("collectionPath", path);
        details.put("operation", operation);
        details.put("includedCount", included.size());
        details.put("aggregateValue", aggregate);
        details.put("operator", comparison);
        details.put("threshold", threshold);
        details.put("matched", matched);
        return new ConditionEvaluationResult(matched, aggregate, details);
    }

    private ConditionEvaluationResult evaluateLegacyTotalCount(Map<String, Object> node,
            ConditionEvaluationContext context) {
        String path = required(node, "collectionPath");
        enforceExecutionGuard(path, context);
        ConditionEvaluationContext.ResolvedPath resolved = context.resolve(path);
        List<?> records = requireCollection(resolved, path);
        return compareCount(records.size(), node, path, "COUNT_TOTAL");
    }

    private ConditionEvaluationResult evaluateLegacyMatchingCount(Map<String, Object> node,
            ConditionEvaluationContext context) {
        String path = required(node, "collectionPath");
        enforceExecutionGuard(path, context);
        ConditionEvaluationContext.ResolvedPath resolved = context.resolve(path);
        List<?> records = requireCollection(resolved, path);
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("type", "CONDITION");
        predicate.put("fieldPath", required(node, "fieldPath"));
        predicate.put("operator", required(node, "operator"));
        if (node.containsKey("threshold")) predicate.put("threshold", node.get("threshold"));
        if (node.containsKey("value")) predicate.put("value", node.get("value"));
        int matching = 0;
        for (Object record : records) {
            if (evaluateScalarCondition(predicate, context.forElement(record)).matched()) matching++;
        }
        return compareCount(matching, node, path, "COUNT_MATCHING");
    }

    private ConditionEvaluationResult compareCount(long count, Map<String, Object> node,
            String path, String type) {
        Object thresholdValue = node.get("countThreshold");
        if (thresholdValue == null) throw new IllegalArgumentException("Count condition requires 'countThreshold'.");
        long threshold;
        try {
            threshold = new BigDecimal(String.valueOf(thresholdValue)).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Count threshold must be a whole number.");
        }
        if (threshold < 0) throw new IllegalArgumentException("Count threshold cannot be negative.");
        String operator = required(node, "countOperator").toUpperCase(Locale.ROOT);
        boolean matched = compareNumbers(BigDecimal.valueOf(count), BigDecimal.valueOf(threshold), operator);
        Map<String, Object> details = Map.of("nodeType", type, "collectionPath", path,
                "count", count, "operator", operator, "threshold", threshold, "matched", matched);
        return new ConditionEvaluationResult(matched, count, details);
    }

    private List<?> requireCollection(ConditionEvaluationContext.ResolvedPath resolved, String path) {
        if (!resolved.found() || resolved.values().size() != 1
                || !(resolved.values().get(0) instanceof List<?> records)) {
            throw new IllegalArgumentException("Collection path must resolve to one array: " + path);
        }
        return (List<?>) resolved.values().get(0);
    }

    private boolean evaluateAny(List<Object> values, String operator, Object expected) {
        boolean evaluated = false;
        IllegalArgumentException lastError = null;
        for (Object actual : values) {
            try {
                boolean matched = evaluateScalarOperator(operator, actual, expected);
                evaluated = true;
                if (matched) return true;
            } catch (IllegalArgumentException exception) {
                lastError = exception;
            }
        }
        if (!evaluated && lastError != null) throw lastError;
        return false;
    }

    private boolean evaluateScalarOperator(String operator, Object actual, Object expected) {
        return switch (operator) {
            case "GREATER_THAN", "GT" -> compareNumbers(actual, expected, "GT");
            case "GREATER_THAN_OR_EQUAL", "GTE" -> compareNumbers(actual, expected, "GTE");
            case "LESS_THAN", "LT" -> compareNumbers(actual, expected, "LT");
            case "LESS_THAN_OR_EQUAL", "LTE" -> compareNumbers(actual, expected, "LTE");
            case "EQUALS", "EQ" -> valuesEqual(actual, expected);
            case "NOT_EQUALS", "NE" -> !valuesEqual(actual, expected);
            case "EQUALS_IGNORE_CASE", "EQ_IGNORE_CASE" -> actual != null && expected != null
                    && String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
            case "CONTAINS" -> actual != null && expected != null
                    && (actual instanceof List<?> list ? list.stream().anyMatch(value -> valuesEqual(value, expected))
                    : String.valueOf(actual).contains(String.valueOf(expected)));
            case "NOT_CONTAINS" -> actual != null && expected != null
                    && !(actual instanceof List<?> list ? list.stream().anyMatch(value -> valuesEqual(value, expected))
                    : String.valueOf(actual).contains(String.valueOf(expected)));
            case "STARTS_WITH" -> actual != null && expected != null
                    && String.valueOf(actual).startsWith(String.valueOf(expected));
            case "ENDS_WITH" -> actual != null && expected != null
                    && String.valueOf(actual).endsWith(String.valueOf(expected));
            case "REGEX" -> regex(actual, expected);
            case "IS_TRUE" -> actual instanceof Boolean value && value;
            case "IS_FALSE" -> actual instanceof Boolean value && !value;
            case "IS_NULL" -> actual == null;
            case "IS_NOT_NULL" -> actual != null;
            default -> throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        };
    }

    private boolean compareNumbers(Object actual, Object expected, String operator) {
        if (!(actual instanceof Number) || !(expected instanceof Number)) {
            throw new IllegalArgumentException("Numeric comparison requires numeric field and threshold values.");
        }
        int comparison = new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected)));
        return switch (operator) {
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> throw new IllegalArgumentException("Unsupported numeric comparison operator.");
        };
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        if (actual instanceof Number && (expected instanceof Number || expected instanceof CharSequence)) {
            try {
                return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected))) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return actual.equals(expected) || String.valueOf(actual).equals(String.valueOf(expected));
    }

    private boolean regex(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        String expression = String.valueOf(expected);
        if (expression.length() > MAX_REGEX_LENGTH) {
            throw new IllegalArgumentException("Regex condition exceeds the maximum length.");
        }
        // Reject common constructs that enable catastrophic backtracking.
        if (isUnsafeRegex(expression)) {
            throw new IllegalArgumentException("Regex uses a construct that is not supported by the safe evaluator.");
        }
        String input = String.valueOf(actual);
        if (input.length() > 100_000) {
            throw new IllegalArgumentException("Regex input exceeds the maximum length.");
        }
        try {
            return Pattern.compile(expression).matcher(input).find();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Regex condition is invalid.");
        }
    }

    private boolean isUnsafeRegex(String expression) {
        if (expression.contains("(?=") || expression.contains("(?!")
                || expression.contains("(?<=") || expression.contains("(?<!")) return true;
        for (int i = 0; i + 1 < expression.length(); i++) {
            if (expression.charAt(i) == '\\' && Character.isDigit(expression.charAt(i + 1))
                    && expression.charAt(i + 1) != '0') return true;
        }

        List<boolean[]> groups = new ArrayList<>();
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (current == '(') {
                groups.add(new boolean[2]); // quantified content, alternation
            } else if (current == ')') {
                if (groups.isEmpty()) continue;
                boolean[] group = groups.remove(groups.size() - 1);
                char next = i + 1 < expression.length() ? expression.charAt(i + 1) : '\0';
                boolean quantifiedGroup = next == '*' || next == '+' || next == '?' || next == '{';
                if (quantifiedGroup && (group[0] || group[1])) return true;
                if (!groups.isEmpty()) {
                    boolean[] parent = groups.get(groups.size() - 1);
                    parent[0] |= group[0] || quantifiedGroup;
                    parent[1] |= group[1];
                }
            } else if (!groups.isEmpty()) {
                boolean[] group = groups.get(groups.size() - 1);
                if (current == '|') group[1] = true;
                if (current == '*' || current == '+' || current == '?' || current == '{') {
                    boolean groupPrefix = current == '?' && i > 0 && expression.charAt(i - 1) == '(';
                    if (!groupPrefix) group[0] = true;
                }
            }
        }
        return false;
    }

    private boolean compareNumbers(BigDecimal actual, BigDecimal expected, String operator) {
        int comparison = actual.compareTo(expected);
        return switch (operator) {
            case "GREATER_THAN", "GT" -> comparison > 0;
            case "GREATER_THAN_OR_EQUAL", "GTE" -> comparison >= 0;
            case "LESS_THAN", "LT" -> comparison < 0;
            case "LESS_THAN_OR_EQUAL", "LTE" -> comparison <= 0;
            case "EQUALS", "EQ" -> comparison == 0;
            case "NOT_EQUALS", "NE" -> comparison != 0;
            default -> throw new IllegalArgumentException("Unsupported count comparison operator: " + operator);
        };
    }

    private void enforceExecutionGuard(String path, ConditionEvaluationContext context) {
        Object executionStatus = context.metadata("executionStatus");
        List<String> allowed = List.of("executionStatus", "executionState", "errorMessage",
                "triggerSource", "resultStatus", "status", "message",
                "response.status", "data.status", "response.message", "data.message",
                "response.errorMessage", "data.errorMessage");
        if (executionStatus != null && !"SUCCESS".equals(String.valueOf(executionStatus))
                && !allowed.contains(path)) {
            throw new IllegalArgumentException("Monitoring execution did not complete successfully; result-based condition was not evaluated.");
        }
        String resultStatus = String.valueOf(context.metadata("resultStatus")).toUpperCase(Locale.ROOT);
        String payloadStatus = String.valueOf(context.metadata("status")).toUpperCase(Locale.ROOT);
        if ((List.of("FAILED", "FAILURE", "ERROR").contains(resultStatus)
                || List.of("FAILED", "FAILURE", "ERROR").contains(payloadStatus)) && !allowed.contains(path)) {
            throw new IllegalArgumentException("Monitoring result reports failure; result-based condition was not evaluated.");
        }
    }

    private Object observed(List<Object> values, boolean found) {
        if (!found) return null;
        if (values.size() == 1) return values.get(0);
        if (values.size() > MAX_EXPLANATION_ITEMS) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("values", values.subList(0, MAX_EXPLANATION_ITEMS));
            summary.put("totalCount", values.size());
            summary.put("truncated", true);
            return summary;
        }
        return values;
    }

    private String required(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Condition configuration requires '" + key + "'.");
        }
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
