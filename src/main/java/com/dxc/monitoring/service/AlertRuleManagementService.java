package com.dxc.monitoring.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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

        Map<String, Object> condition = buildConfiguredCondition(form);
        Map<String, Object> recovery;
        if (form.getRecoveryFieldPath() == null || form.getRecoveryFieldPath().isBlank()) {
            recovery = existingGroupedRecovery(rule);
        } else {
            recovery = new LinkedHashMap<>();
            recovery.put("enabled", true);
            recovery.putAll(buildCondition(form.getRecoveryFieldPath(), form.getRecoveryOperator(),
                    form.getRecoveryThreshold(), form.getRecoveryConsecutiveMatches(), null, "recovery"));
        }
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
            form.setConditionTreeJson(toJson(editableConditionNode(condition)));
            String storedConditionType = stringValue(condition.get("type"), "CONDITION");
            form.setConditionType("GROUP".equalsIgnoreCase(storedConditionType) ? "CONDITION" : storedConditionType);
            form.setCollectionPath(stringValue(condition.get("collectionPath"), ""));
            form.setCountFieldPath(stringValue(condition.get("fieldPath"), ""));
            form.setCountFieldOperator(stringValue(condition.get("operator"), "EQUALS"));
            form.setCountFieldThreshold(stringValue(condition.get("threshold"), stringValue(condition.get("value"), "")));
            form.setCountOperator(stringValue(condition.get("countOperator"), "GREATER_THAN_OR_EQUAL"));
            form.setCountThreshold(stringValue(condition.get("countThreshold"), "1"));
            form.setConsecutiveMatches(integerValue(condition.get("consecutiveMatches"), 1));
            form.setCorrelationFieldPath(stringValue(condition.get("correlationFieldPath"), ""));
            Map<String, Object> recovery = objectMapper.readValue(rule.getRecoveryConfig(), Map.class);
            form.setRecoveryEnabled(!Boolean.FALSE.equals(recovery.get("enabled")));
            form.setRecoveryFieldPath(stringValue(recovery.get("fieldPath"), ""));
            form.setRecoveryOperator(stringValue(recovery.get("operator"), "LESS_THAN_OR_EQUAL"));
            form.setRecoveryThreshold(stringValue(recovery.get("threshold"), stringValue(recovery.get("value"), "")));
            form.setRecoveryConsecutiveMatches(integerValue(recovery.get("consecutiveMatches"), 1));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored alert rule configuration could not be loaded.", exception);
        }
        return form;
    }

    private Map<String, Object> existingGroupedRecovery(AlertRule rule) {
        if (rule.getId() != null && rule.getRecoveryConfig() != null && !rule.getRecoveryConfig().isBlank()) {
            try {
                Map<String, Object> existing = objectMapper.readValue(rule.getRecoveryConfig(), Map.class);
                if ("GROUP".equalsIgnoreCase(stringValue(existing.get("type"), ""))) {
                    return new LinkedHashMap<>(existing);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Stored recovery configuration could not be loaded.", exception);
            }
        }
        throw new IllegalArgumentException("Configure a recovery field path before saving this rule.");
    }

    private Map<String, Object> buildConfiguredCondition(AlertRuleForm form) {
        String type = form.getConditionType() == null ? "CONDITION"
                : form.getConditionType().trim().toUpperCase(Locale.ROOT);
        if ("CONDITION".equals(type)) {
            Map<String, Object> root = parseConditionTree(form.getConditionTreeJson());
            root.put("consecutiveMatches", form.getConsecutiveMatches());
            if (form.getCorrelationFieldPath() != null && !form.getCorrelationFieldPath().isBlank()) {
                String path = form.getCorrelationFieldPath().trim();
                if (path.length() > 255 || !FIELD_PATH.matcher(path).matches()) {
                    throw new IllegalArgumentException("Correlation field path must use dot notation.");
                }
                root.put("correlationFieldPath", path);
            }
            return root;
        }
        if (!"COUNT_TOTAL".equals(type) && !"COUNT_MATCHING".equals(type)) {
            throw new IllegalArgumentException("Choose a supported alert condition type.");
        }
        String collectionPath = form.getCollectionPath() == null ? "" : form.getCollectionPath().trim();
        if (collectionPath.length() > 255 || !FIELD_PATH.matcher(collectionPath).matches()) {
            throw new IllegalArgumentException("Collection field path must use dot notation, for example data.servers.");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", type);
        config.put("collectionPath", collectionPath);
        if ("COUNT_MATCHING".equals(type)) {
            Map<String, Object> rowCondition = buildCondition(form.getCountFieldPath(),
                    form.getCountFieldOperator(), form.getCountFieldThreshold(), 1, null, "count row");
            config.put("fieldPath", rowCondition.get("fieldPath"));
            config.put("operator", rowCondition.get("operator"));
            if (rowCondition.containsKey("threshold")) config.put("threshold", rowCondition.get("threshold"));
        }
        String countOperator = form.getCountOperator() == null ? ""
                : form.getCountOperator().trim().toUpperCase(Locale.ROOT);
        Set<String> countOperators = Set.of("GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN",
                "LESS_THAN_OR_EQUAL", "EQUALS", "NOT_EQUALS");
        if (!countOperators.contains(countOperator)) {
            throw new IllegalArgumentException("Choose a supported count comparison operator.");
        }
        String countThreshold = form.getCountThreshold() == null ? "" : form.getCountThreshold().trim();
        try {
            long parsed = new BigDecimal(countThreshold).longValueExact();
            if (parsed < 0) throw new IllegalArgumentException("Count threshold cannot be negative.");
            config.put("countThreshold", parsed);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Count threshold must be a non-negative whole number.");
        }
        config.put("countOperator", countOperator);
        config.put("consecutiveMatches", form.getConsecutiveMatches());
        if (form.getConsecutiveMatches() < 1 || form.getConsecutiveMatches() > 100) {
            throw new IllegalArgumentException("Consecutive matches must be between 1 and 100.");
        }
        if (form.getCorrelationFieldPath() != null && !form.getCorrelationFieldPath().isBlank()) {
            String correlationPath = form.getCorrelationFieldPath().trim();
            if (correlationPath.length() > 255 || !FIELD_PATH.matcher(correlationPath).matches()) {
                throw new IllegalArgumentException("Correlation field path must use dot notation.");
            }
            config.put("correlationFieldPath", correlationPath);
        }
        return config;
    }

    private Map<String, Object> parseConditionTree(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Configure at least one alert condition.");
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            int[] nodeCount = {0};
            Map<String, Object> root = normalizeConditionNode(parsed, 0, nodeCount);
            if (nodeCount[0] > 100) {
                throw new IllegalArgumentException("An alert condition tree cannot contain more than 100 nodes.");
            }
            return root;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The alert condition tree is invalid.");
        }
    }

    private Map<String, Object> editableConditionNode(Map<String, Object> storedNode) {
        String type = stringValue(storedNode.get("type"), "CONDITION").toUpperCase(Locale.ROOT);
        Map<String, Object> node = new LinkedHashMap<>();
        if ("GROUP".equals(type)) {
            node.put("type", "GROUP");
            node.put("operator", stringValue(storedNode.get("operator"), "AND"));
            Object rawChildren = storedNode.get("children");
            List<Map<String, Object>> children = new java.util.ArrayList<>();
            if (rawChildren instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        throw new IllegalStateException("Stored alert condition group contains an invalid child.");
                    }
                    Map<String, Object> child = new LinkedHashMap<>();
                    raw.forEach((key, value) -> child.put(String.valueOf(key), value));
                    children.add(editableConditionNode(child));
                }
            }
            node.put("children", children);
            return node;
        }
        node.put("type", "CONDITION");
        node.put("fieldPath", stringValue(storedNode.get("fieldPath"), ""));
        node.put("operator", stringValue(storedNode.get("operator"), "GREATER_THAN"));
        if (storedNode.containsKey("threshold")) node.put("threshold", storedNode.get("threshold"));
        else if (storedNode.containsKey("value")) node.put("threshold", storedNode.get("value"));
        return node;
    }

    private Map<String, Object> normalizeConditionNode(Object rawNode, int depth, int[] nodeCount) {
        if (depth > 8) {
            throw new IllegalArgumentException("Condition groups cannot be nested more than 8 levels deep.");
        }
        if (!(rawNode instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Each condition must be a valid condition or group.");
        }
        nodeCount[0]++;
        if (nodeCount[0] > 100) {
            throw new IllegalArgumentException("An alert condition tree cannot contain more than 100 nodes.");
        }
        Map<String, Object> node = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> node.put(String.valueOf(key), value));
        String nodeType = stringValue(node.get("type"), "CONDITION").toUpperCase(Locale.ROOT);
        if ("GROUP".equals(nodeType)) {
            String operator = stringValue(node.get("operator"), "").toUpperCase(Locale.ROOT);
            if (!Set.of("AND", "OR").contains(operator)) {
                throw new IllegalArgumentException("Choose AND or OR for each condition group.");
            }
            Object rawChildren = node.get("children");
            if (!(rawChildren instanceof java.util.List<?> children) || children.isEmpty()) {
                throw new IllegalArgumentException("Each condition group must contain at least one child.");
            }
            List<Map<String, Object>> normalizedChildren = new java.util.ArrayList<>();
            for (Object child : children) {
                normalizedChildren.add(normalizeConditionNode(child, depth + 1, nodeCount));
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("type", "GROUP");
            group.put("operator", operator);
            group.put("children", normalizedChildren);
            return group;
        }
        if (!"CONDITION".equals(nodeType)) {
            throw new IllegalArgumentException("Only field conditions and AND / OR groups can be used in the condition tree.");
        }
        Map<String, Object> condition = buildCondition(stringValue(node.get("fieldPath"), ""),
                stringValue(node.get("operator"), "GREATER_THAN"),
                stringValue(node.get("threshold"), stringValue(node.get("value"), "")),
                1, null, "condition");
        condition.remove("consecutiveMatches");
        condition.put("type", "CONDITION");
        return condition;
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
