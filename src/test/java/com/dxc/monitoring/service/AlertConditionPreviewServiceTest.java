package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.alert.condition.ConditionEvaluator;

import tools.jackson.databind.ObjectMapper;

class AlertConditionPreviewServiceTest {
    private final AlertConditionPreviewService service =
            new AlertConditionPreviewService(new ObjectMapper(), new ConditionEvaluator());

    @Test
    void previewsAnUnsavedCollectionConditionAgainstSampleJson() {
        String sample = """
                {"data":{"consumerGroups":{"items":[
                  {"status":"DOWN","lag":250},
                  {"status":"UP","lag":500}
                ]}}}
                """;
        String condition = """
                {"type":"COLLECTION","collectionPath":"data.consumerGroups.items","quantifier":"ANY",
                 "condition":{"type":"GROUP","operator":"AND","children":[
                   {"type":"CONDITION","fieldPath":"status","operator":"EQUALS","threshold":"DOWN"},
                   {"type":"CONDITION","fieldPath":"lag","operator":"GREATER_THAN","threshold":100}
                 ]}}
                """;

        assertTrue(service.preview(sample, condition).matched());
    }

    @Test
    void returnsNoMatchWithoutCreatingAnAlertWhenConditionDoesNotMatch() {
        String sample = """
                {"data":{"nodes":[{"state":"UP"},{"state":"UP"}]}}
                """;
        String condition = """
                {"type":"COLLECTION","collectionPath":"data.nodes","quantifier":"ANY",
                 "condition":{"fieldPath":"state","operator":"EQUALS","threshold":"DOWN"}}
                """;

        assertFalse(service.preview(sample, condition).matched());
    }

    @Test
    void rejectsInvalidSampleJsonWithAnActionableMessage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.preview("{not-json}", """
                        {"fieldPath":"data.status","operator":"EXISTS"}
                        """));
        assertTrue(exception.getMessage().contains("valid JSON"));
    }
}
