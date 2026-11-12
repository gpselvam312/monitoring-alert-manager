package com.dxc.monitoring.alert.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ConditionConfigurationValidatorTest {
    private final ConditionConfigurationValidator validator = new ConditionConfigurationValidator();

    @Test
    void normalizesCollectionAndAggregateTrees() {
        Map<String, Object> result = validator.normalize(Map.of(
                "type", "GROUP", "operator", "AND",
                "children", java.util.List.of(
                        Map.of("type", "COLLECTION", "collectionPath", "data.nodes", "quantifier", "ANY",
                                "condition", Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN")),
                        Map.of("type", "AGGREGATE", "collectionPath", "data.nodes", "operation", "SUM",
                                "valuePath", "lag", "operator", "GT", "threshold", 100,
                                "filter", Map.of("fieldPath", "state", "operator", "EQ", "threshold", "DOWN")))));

        assertEquals("GROUP", result.get("type"));
        assertEquals("COLLECTION", ((Map<?, ?>) ((java.util.List<?>) result.get("children")).get(0)).get("type"));
        assertEquals("AGGREGATE", ((Map<?, ?>) ((java.util.List<?>) result.get("children")).get(1)).get("type"));
    }

    @Test
    void rejectsMalformedFieldPathsAndUnsupportedOperators() {
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(
                Map.of("fieldPath", "data..cpu", "operator", "GT", "threshold", 80)));
        assertThrows(IllegalArgumentException.class, () -> validator.normalize(
                Map.of("fieldPath", "data.cpu", "operator", "EXECUTE", "threshold", 80)));
    }

    @Test
    void preservesLegacyCountConfiguration() {
        Map<String, Object> result = validator.normalize(Map.of("type", "COUNT_TOTAL",
                "collectionPath", "data.servers", "countOperator", "GREATER_THAN_OR_EQUAL",
                "countThreshold", 2));
        assertEquals(2L, result.get("countThreshold"));
    }
}
