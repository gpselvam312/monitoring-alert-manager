package com.dxc.monitoring.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.dxc.monitoring.alert.condition.ConditionConfigurationValidator;
import com.dxc.monitoring.alert.condition.ConditionEvaluationContext;
import com.dxc.monitoring.alert.condition.ConditionEvaluationResult;
import com.dxc.monitoring.alert.condition.ConditionEvaluator;
import com.dxc.monitoring.alert.json.JsonDocument;

import tools.jackson.databind.ObjectMapper;

/**
 * Evaluates an unsaved alert condition against a supplied JSON sample.
 * This service is deliberately isolated from alert lifecycle and notification services.
 */
@Service
public class AlertConditionPreviewService {
    private static final int MAX_SAMPLE_LENGTH = 1_000_000;
    private static final int MAX_CONDITION_LENGTH = 50_000;

    private final ObjectMapper objectMapper;
    private final ConditionEvaluator evaluator;
    private final ConditionConfigurationValidator validator = new ConditionConfigurationValidator();

    public AlertConditionPreviewService(ObjectMapper objectMapper, ConditionEvaluator evaluator) {
        this.objectMapper = objectMapper;
        this.evaluator = evaluator;
    }

    public Preview preview(String sampleResponse, String conditionJson) {
        if (sampleResponse == null || sampleResponse.isBlank()) {
            throw new IllegalArgumentException("Paste a JSON sample response before testing the condition.");
        }
        if (sampleResponse.length() > MAX_SAMPLE_LENGTH) {
            throw new IllegalArgumentException("Sample response exceeds the 1 MB preview limit.");
        }
        if (conditionJson == null || conditionJson.isBlank()) {
            throw new IllegalArgumentException("Build a condition before testing it.");
        }
        if (conditionJson.length() > MAX_CONDITION_LENGTH) {
            throw new IllegalArgumentException("Condition configuration exceeds the preview limit.");
        }

        Object response;
        Object rawCondition;
        try {
            response = objectMapper.readValue(sampleResponse, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Sample response must be valid JSON.");
        }
        try {
            rawCondition = objectMapper.readValue(conditionJson, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Condition configuration is not valid JSON.");
        }
        Map<String, Object> normalized;
        try {
            normalized = validator.normalize(rawCondition);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
        ConditionEvaluationResult result = evaluator.evaluate(normalized,
                new ConditionEvaluationContext(JsonDocument.from(response), Map.of()));
        return new Preview(result.matched(), result.observedValue(), result.details());
    }

    public record Preview(boolean matched, Object observedValue, Map<String, Object> details) {}
}
