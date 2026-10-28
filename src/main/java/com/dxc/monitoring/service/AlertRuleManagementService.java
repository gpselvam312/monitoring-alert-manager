package com.dxc.monitoring.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.web.AlertRuleForm;

import tools.jackson.databind.ObjectMapper;

@Service
public class AlertRuleManagementService {
    private static final Set<String> OPERATORS = Set.of(
            "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
            "EQUALS", "NOT_EQUALS", "EQUALS_IGNORE_CASE", "CONTAINS", "REGEX",
            "EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE");
    private static final Set<String> THRESHOLD_FREE_OPERATORS = Set.of("EXISTS", "NOT_EXISTS", "IS_TRUE", "IS_FALSE");
    private static final Pattern FIELD_PATH = Pattern.compile("[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9_\\-]+)*");

    private final AlertRuleRepository ruleRepository;
    private final MonitoringJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final NotificationPolicyRepository notificationPolicyRepository;

    public AlertRuleManagementService(AlertRuleRepository ruleRepository,
            MonitoringJobRepository jobRepository, ObjectMapper objectMapper,
            NotificationPolicyRepository notificationPolicyRepository) {
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.notificationPolicyRepository = notificationPolicyRepository;
    }

    @Transactional
    public AlertRule save(AlertRuleForm form, Long userId) {
        if (form == null) throw new IllegalArgumentException("Alert rule form is required.");
        if (form.getMonitoringJobId() == null) throw new IllegalArgumentException("Select a monitoring job.");
        MonitoringJob job = jobRepository.findByIdForDetails(form.getMonitoringJobId())
                .orElseThrow(() -> new IllegalArgumentException("Selected monitoring job was not found."));
        if (job.getExecutionMode() != MonitoringJob.ExecutionMode.STANDARD) {
            throw new IllegalArgumentException("Alert rules can only be attached to standard monitoring jobs.");
        }

        AlertRule rule;
        if (form.getId() == null) {
            rule = new AlertRule();
            rule.setCreatedBy(userId);
        } else {
            rule = ruleRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Alert rule was not found."));
            if (!rule.getMonitoringJob().getId().equals(job.getId())) {
                throw new IllegalArgumentException("An existing alert rule cannot be moved to another monitoring job.");
            }
        }

        String name = form.getName() == null ? "" : form.getName().trim();
        if (name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Rule name is required and must not exceed 150 characters.");
        }
        if (form.getConsecutiveMatches() < 1 || form.getConsecutiveMatches() > 100) {
            throw new IllegalArgumentException("Consecutive matches must be between 1 and 100.");
        }
        if (!form.isRecoveryEnabled()) {
            throw new IllegalArgumentException("Recovery criteria must remain enabled so active alerts can be resolved safely.");
        }
        if (form.getRecoveryConsecutiveMatches() < 1 || form.getRecoveryConsecutiveMatches() > 100) {
            throw new IllegalArgumentException("Consecutive recovery matches must be between 1 and 100.");
        }

        rule.setMonitoringJob(job);
        rule.setName(name);
        rule.setDescription(trimToNull(form.getDescription()));
        if (form.getNotificationPolicyId() != null) {
            notificationPolicyRepository.findById(form.getNotificationPolicyId()).filter(com.dxc.monitoring.entity.NotificationPolicy::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Selected notification policy was not found."));
        }
        rule.setNotificationPolicyId(form.getNotificationPolicyId());
        rule.setEnabled(form.isEnabled());
        try {
            rule.setSeverity(AlertRule.Severity.valueOf(form.getSeverity()));
            rule.setExecutionSourcePolicy(AlertRule.ExecutionSourcePolicy.valueOf(form.getExecutionSourcePolicy()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Choose a supported severity and execution source policy.");
        }

        Map<String, Object> condition = buildCondition(form.getConditionFieldPath(),
                form.getConditionOperator(), form.getThreshold(), form.getConsecutiveMatches(),
                form.getCorrelationFieldPath(), "condition");
        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("enabled", true);
        recovery.putAll(buildCondition(form.getRecoveryFieldPath(), form.getRecoveryOperator(),
                form.getRecoveryThreshold(), form.getRecoveryConsecutiveMatches(), null, "recovery"));
        rule.setConditionConfig(toJson(condition));
        rule.setRecoveryConfig(toJson(recovery));
        rule.setUpdatedBy(userId);
        return ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public AlertRuleForm toForm(AlertRule rule) {
        AlertRuleForm form = new AlertRuleForm();
        form.setId(rule.getId());
        form.setMonitoringJobId(rule.getMonitoringJob().getId());
        form.setName(rule.getName());
        form.setDescription(rule.getDescription());
        form.setEnabled(rule.isEnabled());
        form.setSeverity(rule.getSeverity().name());
        form.setExecutionSourcePolicy(rule.getExecutionSourcePolicy().name());
        form.setNotificationPolicyId(rule.getNotificationPolicyId());
        try {
            Map<String, Object> condition = objectMapper.readValue(rule.getConditionConfig(), Map.class);
            form.setConditionFieldPath(stringValue(condition.get("fieldPath"), ""));
            form.setConditionOperator(stringValue(condition.get("operator"), "GREATER_THAN"));
            form.setThreshold(stringValue(condition.get("threshold"), stringValue(condition.get("value"), "")));
            form.setConsecutiveMatches(integerValue(condition.get("consecutiveMatches"), 1));
            form.setCorrelationFieldPath(stringValue(condition.get("correlationFieldPath"), ""));
            Map<String, Object> recovery = objectMapper.readValue(rule.getRecoveryConfig(), Map.class);
            form.setRecoveryEnabled(!Boolean.FALSE.equals(recovery.get("enabled"))
                    && recovery.containsKey("fieldPath"));
            form.setRecoveryFieldPath(stringValue(recovery.get("fieldPath"), ""));
            form.setRecoveryOperator(stringValue(recovery.get("operator"), "LESS_THAN_OR_EQUAL"));
            form.setRecoveryThreshold(stringValue(recovery.get("threshold"), stringValue(recovery.get("value"), "")));
            form.setRecoveryConsecutiveMatches(integerValue(recovery.get("consecutiveMatches"), 1));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored alert rule configuration could not be loaded.", exception);
        }
        return form;
    }

    private Map<String, Object> buildCondition(String fieldPath, String operator, String threshold,
            int consecutiveMatches, String correlationFieldPath, String label) {
        String normalizedPath = fieldPath == null ? "" : fieldPath.trim();
        if (normalizedPath.length() > 255 || !FIELD_PATH.matcher(normalizedPath).matches()) {
            throw new IllegalArgumentException("The " + label + " field path must use dot notation, for example data.cpu.usage.");
        }
        String normalizedOperator = operator == null ? "" : operator.trim().toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(normalizedOperator)) {
            throw new IllegalArgumentException("Choose a supported " + label + " operator.");
        }
        if (consecutiveMatches < 1 || consecutiveMatches > 100) {
            throw new IllegalArgumentException("Consecutive matches must be between 1 and 100.");
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("fieldPath", normalizedPath);
        config.put("operator", normalizedOperator);
        if (!THRESHOLD_FREE_OPERATORS.contains(normalizedOperator)) {
            String normalizedThreshold = threshold == null ? "" : threshold.trim();
            if (normalizedThreshold.isBlank()) {
                throw new IllegalArgumentException("A threshold/value is required for the " + label + " condition.");
            }
            if (Set.of("GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL").contains(normalizedOperator)) {
                try {
                    config.put("threshold", new BigDecimal(normalizedThreshold));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Numeric comparison thresholds must be valid numbers.");
                }
            } else {
                if (normalizedThreshold.length() > 500) throw new IllegalArgumentException("Condition value is too long.");
                config.put("threshold", normalizedThreshold);
            }
        }
        config.put("consecutiveMatches", consecutiveMatches);
        if (correlationFieldPath != null && !correlationFieldPath.isBlank()) {
            String normalizedCorrelationPath = correlationFieldPath.trim();
            if (normalizedCorrelationPath.length() > 255 || !FIELD_PATH.matcher(normalizedCorrelationPath).matches()) {
                throw new IllegalArgumentException("Correlation field path must use dot notation.");
            }
            config.put("correlationFieldPath", normalizedCorrelationPath);
        }
        return config;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Alert rule configuration could not be serialized.", exception);
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int integerValue(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
