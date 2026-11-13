package com.dxc.monitoring.alert.condition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Matrix coverage for every condition operator accepted by save-time validation.
 * Runtime semantics are exercised separately by ConditionEvaluatorTest.
 */
class ConditionConfigurationMatrixTest {
    private final ConditionConfigurationValidator validator = new ConditionConfigurationValidator();

    private static final Set<String> VALUE_OPERATORS = Set.of(
            "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
            "GT", "GTE", "LT", "LTE", "EQUALS", "EQ", "NOT_EQUALS", "NE",
            "EQUALS_IGNORE_CASE", "EQ_IGNORE_CASE", "CONTAINS", "NOT_CONTAINS",
            "STARTS_WITH", "ENDS_WITH", "REGEX");
    private static final Set<String> VALUE_FREE_OPERATORS = Set.of(
            "EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE", "IS_NULL", "IS_NOT_NULL");

    @Test
    void acceptsEverySupportedOperatorWithTheCorrectValueRequirement() {
        for (String operator : VALUE_OPERATORS) {
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("type", "CONDITION");
            condition.put("fieldPath", "data.status");
            condition.put("operator", operator);
            condition.put("threshold", Set.of("GREATER_THAN", "GREATER_THAN_OR_EQUAL",
                    "LESS_THAN", "LESS_THAN_OR_EQUAL", "GT", "GTE", "LT", "LTE").contains(operator)
                    ? 10 : "DOWN");
            assertDoesNotThrow(() -> validator.normalize(condition), operator);
        }

        for (String operator : VALUE_FREE_OPERATORS) {
            assertDoesNotThrow(() -> validator.normalize(Map.of(
                    "type", "CONDITION", "fieldPath", "data.status", "operator", operator)), operator);
        }
    }

    @Test
    void rejectsMissingValuesForValueOperatorsAndUnknownOperators() {
        for (String operator : VALUE_OPERATORS) {
            assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                    "type", "CONDITION", "fieldPath", "data.status", "operator", operator)), operator);
        }
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "CONDITION", "fieldPath", "data.status", "operator", "EXECUTE")));
    }

    @Test
    void acceptsBothGroupOperatorsAndRejectsMalformedGroups() {
        for (String operator : List.of("AND", "OR")) {
            assertDoesNotThrow(() -> validator.normalize(Map.of(
                    "type", "GROUP", "operator", operator,
                    "children", List.of(
                            Map.of("fieldPath", "data.a", "operator", "EXISTS"),
                            Map.of("fieldPath", "data.b", "operator", "NOT_EXISTS")))));
        }
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "GROUP", "operator", "XOR",
                "children", List.of(Map.of("fieldPath", "data.a", "operator", "EXISTS"),
                        Map.of("fieldPath", "data.b", "operator", "EXISTS")))));
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "GROUP", "operator", "AND",
                "children", List.of(Map.of("fieldPath", "data.a", "operator", "EXISTS")))));
    }

    @Test
    void acceptsAllCollectionQuantifiersAndRejectsUnknownQuantifiers() {
        Map<String, Object> predicate = Map.of("fieldPath", "status", "operator", "EQUALS", "threshold", "DOWN");
        for (String quantifier : List.of("ANY", "ALL", "NONE")) {
            assertDoesNotThrow(() -> validator.normalize(Map.of(
                    "type", "COLLECTION", "collectionPath", "data.nodes",
                    "quantifier", quantifier, "condition", predicate)), quantifier);
        }
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "SOME", "condition", predicate)));
    }

    @Test
    void acceptsEveryAggregateOperationAndRejectsInvalidAggregateConfiguration() {
        for (String operation : List.of("COUNT", "SUM", "MIN", "MAX")) {
            Map<String, Object> aggregate = new LinkedHashMap<>();
            aggregate.put("type", "AGGREGATE");
            aggregate.put("collectionPath", "data.nodes");
            aggregate.put("operation", operation);
            if (!"COUNT".equals(operation)) aggregate.put("valuePath", "lag");
            aggregate.put("operator", "GTE");
            aggregate.put("threshold", 100);
            assertDoesNotThrow(() -> validator.normalize(aggregate), operation);
        }
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "AGGREGATE", "collectionPath", "data.nodes", "operation", "AVERAGE",
                "valuePath", "lag", "operator", "GTE", "threshold", 100)));
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "type", "AGGREGATE", "collectionPath", "data.nodes", "operation", "SUM",
                "valuePath", "lag", "operator", "GTE", "threshold", "not-a-number")));
    }

    @Test
    void acceptsLegacyCollectionCountModesAndValidatesCountThresholds() {
        for (String type : List.of("COUNT_TOTAL", "COUNT_MATCHING")) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("type", type);
            config.put("collectionPath", "data.nodes");
            if ("COUNT_MATCHING".equals(type)) {
                config.put("fieldPath", "state");
                config.put("operator", "EQUALS");
                config.put("threshold", "DOWN");
            }
            config.put("countOperator", "GREATER_THAN_OR_EQUAL");
            config.put("countThreshold", 2);
            assertDoesNotThrow(() -> validator.normalize(config), type);
        }
        for (Object invalidThreshold : List.of(-1, 1.5, "abc")) {
            assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                    "type", "COUNT_TOTAL", "collectionPath", "data.nodes",
                    "countOperator", "EQ", "countThreshold", invalidThreshold)));
        }
    }

    @Test
    void enforcesFieldPathAndTreeSizeLimits() {
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "fieldPath", "data..status", "operator", "EXISTS")));
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(Map.of(
                "fieldPath", "data." + "x".repeat(260), "operator", "EXISTS")));

        Map<String, Object> tooManyChildren = Map.of("type", "GROUP", "operator", "AND",
                "children", java.util.stream.IntStream.range(0, 100)
                        .mapToObj(i -> Map.<String, Object>of("fieldPath", "data.f" + i, "operator", "EXISTS"))
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(tooManyChildren));
    }
}
