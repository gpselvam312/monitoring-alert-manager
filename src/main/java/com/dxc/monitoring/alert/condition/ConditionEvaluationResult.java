package com.dxc.monitoring.alert.condition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured outcome returned by the condition engine. */
public record ConditionEvaluationResult(boolean matched, Object observedValue, Map<String, Object> details) {
    public ConditionEvaluationResult {
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
