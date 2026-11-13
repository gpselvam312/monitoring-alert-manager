package com.dxc.monitoring.alert.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.alert.json.JsonDocument;

class ConditionEvaluatorTest {
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void evaluatesMetadataAndPayloadWithoutCrossNamespaceFallback() {
        JsonDocument document = JsonDocument.from(Map.of(
                "status", "CRITICAL",
                "data", Map.of("cluster", Map.of("name", "kafka-sit"))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document,
                Map.of("status", "CRITICAL", "executionStatus", "SUCCESS"));

        assertTrue(evaluator.evaluate(condition("response.status", "EQ", "CRITICAL"), context).matched());
        assertTrue(evaluator.evaluate(condition("data.cluster.name", "EQ", "kafka-sit"), context).matched());
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(condition("data.status", "EQ", "CRITICAL"),
                        new ConditionEvaluationContext(document, Map.of())));
    }

    @Test
    void supportsExplicitNullAndMissingValues() {
        JsonDocument document = JsonDocument.from(Map.of("data", java.util.Collections.singletonMap("value", null)));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());

        assertTrue(evaluator.evaluate(Map.of("fieldPath", "data.value", "operator", "IS_NULL"), context).matched());
        assertTrue(evaluator.evaluate(Map.of("fieldPath", "data.missing", "operator", "NOT_EXISTS"), context).matched());
        assertFalse(evaluator.evaluate(Map.of("fieldPath", "data.value", "operator", "NOT_EXISTS"), context).matched());
    }

    @Test
    void evaluatesCollectionQuantifiersWithinEachRecord() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("nodes", java.util.List.of(
                Map.of("name", "a", "state", "DOWN"),
                Map.of("name", "b", "state", "DOWN")))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> collection = Map.of("type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "ALL", "condition",
                Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN"));

        assertTrue(evaluator.evaluate(collection, context).matched());
    }

    @Test
    void scalarConditionOnArrayPathUsesAnyMatchSemantics() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("consumerGroups", Map.of("items", java.util.List.of(
                Map.of("status", "UP"), Map.of("status", "DOWN"))))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());

        assertTrue(evaluator.evaluate(condition("data.consumerGroups.items.status", "EQ", "DOWN"), context).matched());
        assertFalse(evaluator.evaluate(condition("data.consumerGroups.items.status", "EQ", "FAILED"), context).matched());
    }

    @Test
    void collectionNestedAndRequiresBothPredicatesOnTheSameRecord() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("consumerGroups", Map.of("items", java.util.List.of(
                Map.of("status", "DOWN", "lag", 10),
                Map.of("status", "UP", "lag", 500))))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> sameRecord = Map.of("type", "COLLECTION",
                "collectionPath", "data.consumerGroups.items", "quantifier", "ANY",
                "condition", Map.of("type", "GROUP", "operator", "AND", "children", java.util.List.of(
                        condition("status", "EQ", "DOWN"), condition("lag", "GT", 100))));

        assertFalse(evaluator.evaluate(sameRecord, context).matched());
    }

    @Test
    void collectionNestedAndMatchesWhenBothPredicatesBelongToOneRecord() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("consumerGroups", Map.of("items", java.util.List.of(
                Map.of("status", "DOWN", "lag", 250),
                Map.of("status", "UP", "lag", 500))))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> sameRecord = Map.of("type", "COLLECTION",
                "collectionPath", "data.consumerGroups.items", "quantifier", "ANY",
                "condition", Map.of("type", "GROUP", "operator", "AND", "children", java.util.List.of(
                        condition("status", "EQ", "DOWN"), condition("lag", "GT", 100))));

        assertTrue(evaluator.evaluate(sameRecord, context).matched());
    }

    @Test
    void allOverEmptyCollectionIsFalseByDefault() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("nodes", java.util.List.of())));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> collection = Map.of("type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "ALL", "condition",
                Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN"));

        assertFalse(evaluator.evaluate(collection, context).matched());
    }

    @Test
    void collectionQuantifiersHaveExplicitEmptyArraySemantics() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("nodes", java.util.List.of())));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> predicate = Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN");

        assertFalse(evaluator.evaluate(Map.of("type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "ANY", "condition", predicate), context).matched());
        assertTrue(evaluator.evaluate(Map.of("type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "NONE", "condition", predicate), context).matched());
        assertTrue(evaluator.evaluate(Map.of("type", "COLLECTION", "collectionPath", "data.nodes",
                "quantifier", "ALL", "emptyMatches", true, "condition", predicate), context).matched());
    }

    @Test
    void calculatesFilteredNumericAggregate() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("nodes", java.util.List.of(
                Map.of("state", "UP", "lag", 10),
                Map.of("state", "DOWN", "lag", 90),
                Map.of("state", "DOWN", "lag", 30)))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> aggregate = Map.of("type", "AGGREGATE", "collectionPath", "data.nodes",
                "operation", "SUM", "valuePath", "lag", "operator", "GT", "threshold", 100,
                "filter", Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN"));

        ConditionEvaluationResult result = evaluator.evaluate(aggregate, context);
        assertTrue(result.matched());
        assertEquals("120", result.observedValue().toString());
    }

    @Test
    void supportsLegacyUnqualifiedFieldPaths() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("cpu", Map.of("usage", 92))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document,
                Map.of("cpu", Map.of("usage", 92), "data", Map.of("cpu", Map.of("usage", 92))));
        assertTrue(evaluator.evaluate(condition("cpu.usage", "GT", 85), context).matched());
    }


    @Test
    void comparesTwoFieldsWithoutLiteralThresholds() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("actual", 95, "limit", 80)));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> condition = Map.of("type", "FIELD_COMPARISON",
                "leftPath", "data.actual", "rightPath", "data.limit", "operator", "GT");

        assertTrue(evaluator.evaluate(condition, context).matched());
    }


    @Test
    void doesNotTreatAnAbsentResponseAsAnExplicitNullValue() {
        ConditionEvaluationContext context = new ConditionEvaluationContext(JsonDocument.from(null), Map.of());
        assertFalse(evaluator.evaluate(Map.of("fieldPath", "data.value", "operator", "IS_NULL"), context).matched());
        assertTrue(evaluator.evaluate(Map.of("fieldPath", "data.value", "operator", "NOT_EXISTS"), context).matched());
    }


    @Test
    void evaluatesPrimitiveCollectionElementsThroughTheValueField() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("states", java.util.List.of("DOWN", "DOWN"))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> condition = Map.of("type", "COLLECTION", "collectionPath", "data.states",
                "quantifier", "ALL", "condition",
                Map.of("fieldPath", "value", "operator", "EQ", "threshold", "DOWN"));

        assertTrue(evaluator.evaluate(condition, context).matched());
    }


    @Test
    void rejectsRegexPatternsWithNestedBacktrackingQuantifiers() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("value", "aaaaaaaa")));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(
                condition("data.value", "REGEX", "(a+)+$"), context));
    }


    @Test
    void countsNestedCollectionsAcrossParentRecords() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("groups", java.util.List.of(
                Map.of("items", java.util.List.of(Map.of("id", "a"), Map.of("id", "b"))),
                Map.of("items", java.util.List.of(Map.of("id", "c")))))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> condition = Map.of("type", "COUNT_TOTAL",
                "collectionPath", "data.groups.items", "countOperator", "EQ", "countThreshold", 3);

        assertTrue(evaluator.evaluate(condition, context).matched());
    }

    @Test
    void treatsNestedEmptyCollectionsAsZeroRecords() {
        JsonDocument document = JsonDocument.from(Map.of("data", Map.of("groups", java.util.List.of(
                Map.of("items", java.util.List.of()), Map.of("items", java.util.List.of())))));
        ConditionEvaluationContext context = new ConditionEvaluationContext(document, Map.of());
        Map<String, Object> condition = Map.of("type", "COUNT_TOTAL",
                "collectionPath", "data.groups.items", "countOperator", "EQ", "countThreshold", 0);

        assertTrue(evaluator.evaluate(condition, context).matched());
    }

    private Map<String, Object> condition(String path, String operator, Object threshold) {
        return Map.of("fieldPath", path, "operator", operator, "threshold", threshold);
    }
}
