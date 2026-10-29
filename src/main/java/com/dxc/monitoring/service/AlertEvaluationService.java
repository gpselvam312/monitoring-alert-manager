package com.dxc.monitoring.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleEvaluation;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleEvaluationRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AlertEvaluationService {
    private final MonitoringExecutionRepository executionRepository;
    private final MonitoringResultRepository resultRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertRuleEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;
    private final NotificationRoutingService notificationRoutingService;

    public AlertEvaluationService(MonitoringExecutionRepository executionRepository,
            MonitoringResultRepository resultRepository,
            AlertRuleRepository ruleRepository,
            AlertRepository alertRepository,
            AlertHistoryRepository historyRepository,
            AlertRuleEvaluationRepository evaluationRepository,
            ObjectMapper objectMapper, NotificationRoutingService notificationRoutingService) {
        this.executionRepository = executionRepository;
        this.resultRepository = resultRepository;
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.evaluationRepository = evaluationRepository;
        this.objectMapper = objectMapper;
        this.notificationRoutingService = notificationRoutingService;
    }

    @Transactional
    public void evaluateExecution(Long executionId) {
        MonitoringExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring execution not found."));
        if (execution.getStatus() == MonitoringExecution.ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Cannot evaluate an execution that is still running.");
        }

        Map<String, Object> context = buildContext(execution);
        List<AlertRule> rules = ruleRepository.findEnabledForJobForUpdate(execution.getMonitoringJob().getId());
        for (AlertRule rule : rules) {
            if (!sourceAllowed(rule, execution.getTriggerSource())) {
                continue;
            }
            if (evaluationRepository.existsByAlertRuleIdAndExecutionId(rule.getId(), executionId)) {
                continue;
            }

            Optional<AlertRuleEvaluation> previousEvaluation =
                    evaluationRepository.findTopByAlertRule_IdOrderByEvaluatedAtDescIdDesc(rule.getId());
            if (previousEvaluation.isPresent()
                    && previousEvaluation.get().getExecution().getStartedAt() != null
                    && execution.getStartedAt() != null
                    && previousEvaluation.get().getExecution().getStartedAt().isAfter(execution.getStartedAt())) {
                // A newer execution was evaluated first by another worker. Do not
                // let an older queued execution roll alert state backwards.
                continue;
            }

            try {
                evaluateRule(rule, execution, context, previousEvaluation.orElse(null));
            } catch (IllegalArgumentException exception) {
                saveEvaluation(rule, execution, AlertRuleEvaluation.Outcome.ERROR,
                        Map.of("error", safeMessage(exception), "phase", "VALIDATION"));
            }
        }
    }

    private void evaluateRule(AlertRule rule, MonitoringExecution execution, Map<String, Object> context,
            AlertRuleEvaluation previousEvaluation) {
        Map<String, Object> condition = readConfig(rule.getConditionConfig(), "condition");
        String correlationKey = correlationKey(condition, context);
        Optional<Alert> active = alertRepository.findByAlertRuleIdAndCorrelationKeyAndStatus(
                rule.getId(), correlationKey, Alert.AlertStatus.ACTIVE);
        ConditionResult conditionResult;
        if (active.isEmpty()) {
            conditionResult = evaluateCondition(condition, context);
        } else {
            // Recovery criteria are independent. Missing condition data must not
            // prevent a configured NOT_EXISTS recovery condition from resolving an alert.
            try {
                conditionResult = evaluateCondition(condition, context);
            } catch (IllegalArgumentException exception) {
                conditionResult = new ConditionResult(false, null);
            }
        }

        Map<String, Object> evaluationDetails = new LinkedHashMap<>();
        evaluationDetails.put("phase", active.isPresent() ? "RECOVERY" : "CONDITION");
        evaluationDetails.put("fieldPath", condition.get("fieldPath"));
        evaluationDetails.put("conditionMatched", conditionResult.matched());
        evaluationDetails.put("observedValue", conditionResult.value());

        if (active.isEmpty()) {
            int previousCount = previousConsecutiveCount(previousEvaluation, "CONDITION", "consecutiveMatches",
                    "conditionMatched");
            int matchCount = conditionResult.matched() ? previousCount + 1 : 0;
            evaluationDetails.put("consecutiveMatches", matchCount);
            evaluationDetails.put("recoveryMatched", false);
            evaluationDetails.put("consecutiveRecoveryMatches", 0);

            if (conditionResult.matched() && matchCount >= consecutiveCount(condition)) {
                createAlert(rule, execution, correlationKey(condition, context), conditionResult);
            }
            saveEvaluation(rule, execution,
                    conditionResult.matched() ? AlertRuleEvaluation.Outcome.MATCHED : AlertRuleEvaluation.Outcome.NO_MATCH,
                    evaluationDetails);
            return;
        }

        Alert alert = active.get();
        Map<String, Object> recovery = readConfig(rule.getRecoveryConfig(), "recovery");
        boolean recoveryEnabled = !Boolean.FALSE.equals(recovery.get("enabled"))
                && (isConditionNode(recovery) || isConditionGroupNode(recovery));
        boolean recoveryMatched = false;
        Object recoveryValue = null;
        int recoveryCount = 0;

        if (recoveryEnabled) {
            ConditionResult recoveryResult = evaluateCondition(recovery, context);
            recoveryMatched = recoveryResult.matched();
            recoveryValue = recoveryResult.value();
            int previousRecoveryCount = previousConsecutiveCount(previousEvaluation, "RECOVERY",
                    "consecutiveRecoveryMatches", "recoveryMatched");
            recoveryCount = recoveryMatched ? previousRecoveryCount + 1 : 0;
        }

        evaluationDetails.put("recoveryMatched", recoveryMatched);
        evaluationDetails.put("recoveryObservedValue", recoveryValue);
        evaluationDetails.put("consecutiveRecoveryMatches", recoveryCount);
        evaluationDetails.put("recoveryEnabled", recoveryEnabled);

        if (recoveryEnabled && recoveryMatched && recoveryCount >= consecutiveCount(recovery)) {
            OffsetDateTime now = OffsetDateTime.now();
            alert.setStatus(Alert.AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alert.setLastExecution(execution);
            alertRepository.save(alert);
            saveHistory(alert, AlertHistory.EventType.RESOLVED, Alert.AlertStatus.ACTIVE,
                    Alert.AlertStatus.RESOLVED, alert.getSeverity(), alert.getSeverity(), execution,
                    recoveryValue, Map.of("recoveryFieldPath", String.valueOf(recovery.get("fieldPath")),
                            "consecutiveRecoveryMatches", recoveryCount));
        } else if (conditionResult.matched() && !recoveryMatched) {
            OffsetDateTime now = OffsetDateTime.now();
            AlertRule.Severity oldSeverity = alert.getSeverity();
            if (oldSeverity != rule.getSeverity()) {
                alert.setSeverity(rule.getSeverity());
                saveHistory(alert, AlertHistory.EventType.SEVERITY_CHANGED, Alert.AlertStatus.ACTIVE,
                        Alert.AlertStatus.ACTIVE, oldSeverity, rule.getSeverity(), execution,
                        conditionResult.value(), Map.of("ruleName", rule.getName()));
            }
            alert.setCurrentValue(toJson(conditionResult.value()));
            alert.setLastExecution(execution);
            alert.setLastOccurredAt(now);
            alert.setOccurrenceCount(alert.getOccurrenceCount() + 1);
            alert.setSummary(summary(rule, condition, conditionResult.value()));
            alertRepository.save(alert);
            saveHistory(alert, AlertHistory.EventType.OCCURRENCE, Alert.AlertStatus.ACTIVE,
                    Alert.AlertStatus.ACTIVE, oldSeverity, alert.getSeverity(), execution,
                    conditionResult.value(), Map.of("occurrenceCount", alert.getOccurrenceCount()));
        }

        saveEvaluation(rule, execution,
                recoveryMatched || conditionResult.matched()
                        ? AlertRuleEvaluation.Outcome.MATCHED : AlertRuleEvaluation.Outcome.NO_MATCH,
                evaluationDetails);
    }

    private void createAlert(AlertRule rule, MonitoringExecution execution, String correlationKey,
            ConditionResult result) {
        OffsetDateTime now = OffsetDateTime.now();
        Alert alert = new Alert();
        alert.setAlertRule(rule);
        alert.setMonitoringJob(execution.getMonitoringJob());
        alert.setCorrelationKey(correlationKey);
        alert.setStatus(Alert.AlertStatus.ACTIVE);
        alert.setSeverity(rule.getSeverity());
        alert.setTitle("Alert: " + rule.getName());
        alert.setSummary(summary(rule, readConfig(rule.getConditionConfig(), "condition"), result.value()));
        alert.setCurrentValue(toJson(result.value()));
        alert.setFirstOccurredAt(now);
        alert.setLastOccurredAt(now);
        alert.setOccurrenceCount(1L);
        alert.setLastExecution(execution);
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alert = alertRepository.save(alert);
        saveHistory(alert, AlertHistory.EventType.CREATED, null, Alert.AlertStatus.ACTIVE,
                null, alert.getSeverity(), execution, result.value(),
                Map.of("ruleName", rule.getName(), "correlationKey", correlationKey));
    }

    private void saveHistory(Alert alert, AlertHistory.EventType eventType,
            Alert.AlertStatus previousStatus, Alert.AlertStatus newStatus,
            AlertRule.Severity previousSeverity, AlertRule.Severity newSeverity,
            MonitoringExecution execution, Object observedValue, Map<String, Object> details) {
        AlertHistory history = new AlertHistory();
        history.setAlert(alert);
        history.setEventType(eventType);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setPreviousSeverity(previousSeverity);
        history.setNewSeverity(newSeverity);
        history.setExecution(execution);
        history.setObservedValue(toJson(observedValue));
        history.setDetails(toJson(details));
        history.setOccurredAt(OffsetDateTime.now());
        AlertHistory savedHistory = historyRepository.save(history);
        notificationRoutingService.queueForHistory(savedHistory);
    }

    private void saveEvaluation(AlertRule rule, MonitoringExecution execution,
            AlertRuleEvaluation.Outcome outcome, Map<String, Object> details) {
        AlertRuleEvaluation evaluation = new AlertRuleEvaluation();
        evaluation.setAlertRule(rule);
        evaluation.setExecution(execution);
        evaluation.setOutcome(outcome);
        evaluation.setDetails(toJson(details));
        evaluation.setEvaluatedAt(OffsetDateTime.now());
        evaluationRepository.save(evaluation);
    }

    private Map<String, Object> buildContext(MonitoringExecution execution) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("executionStatus", execution.getStatus().name());
        context.put("executionState", execution.getStatus().name());
        context.put("triggerSource", execution.getTriggerSource().name());
        context.put("errorMessage", execution.getErrorMessage());

        List<MonitoringResult> results = resultRepository.findAllByExecutionId(execution.getId());
        if (!results.isEmpty()) {
            MonitoringResult result = results.get(results.size() - 1);
            context.put("resultStatus", result.getStatus() == null ? null : result.getStatus().name());
            context.put("resultValue", result.getValue());
            context.put("resultUnit", result.getUnit());
            boolean messagePayloadFound = false;
            String message = result.getMessage();
            if (message != null && !message.isBlank()) {
                Object parsedMessage = parseStructuredPayload(message);
                if (parsedMessage instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> context.put(String.valueOf(key), value));
                    messagePayloadFound = true;
                } else if (parsedMessage != null) {
                    context.put("data", parsedMessage);
                    messagePayloadFound = true;
                } else {
                    Map<String, Object> messageFields = parseKeyValuePayload(message);
                    if (!messageFields.isEmpty()) {
                        context.putAll(messageFields);
                        messagePayloadFound = true;
                    } else {
                        // Preserve plain-text messages as a selectable field too.
                        context.put("message", message);
                    }
                }
            }

            // Some result types persist structured output in result_data instead of message.
            // Use it as a fallback, while keeping any message field available in the context.
            if (!messagePayloadFound && result.getResultData() != null && !result.getResultData().isBlank()) {
                try {
                    Object parsed = objectMapper.readValue(result.getResultData(), Object.class);
                    if (parsed instanceof Map<?, ?> map) {
                        map.forEach((key, value) -> context.put(String.valueOf(key), value));
                    } else {
                        context.put("data", parsed);
                    }
                } catch (Exception exception) {
                    throw new IllegalArgumentException("The normalized monitoring result is not valid JSON.");
                }
            } else if (!messagePayloadFound && (result.getResultData() == null || result.getResultData().isBlank())
                    && execution.getStatus() == MonitoringExecution.ExecutionStatus.SUCCESS) {
                // No structured result is not equivalent to a healthy value.
                context.put("resultDataMissing", true);
            }
        }
        return context;
    }

    private Object parseStructuredPayload(String payload) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parseKeyValuePayload(String payload) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String line : payload.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator < 1) separator = line.indexOf(':');
            if (separator < 1) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,254}") || value.isEmpty()) continue;
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                values.put(key, Boolean.valueOf(value));
            } else {
                try {
                    values.put(key, new BigDecimal(value));
                } catch (NumberFormatException ignored) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    private boolean sourceAllowed(AlertRule rule, MonitoringExecution.TriggerSource source) {
        if (source == null || source == MonitoringExecution.TriggerSource.UNKNOWN) {
            return false;
        }
        return switch (rule.getExecutionSourcePolicy()) {
            case SCHEDULED_ONLY -> source == MonitoringExecution.TriggerSource.SCHEDULED;
            case MANUAL_ONLY -> source == MonitoringExecution.TriggerSource.MANUAL;
            case ALL_NON_STREAMING -> source == MonitoringExecution.TriggerSource.SCHEDULED
                    || source == MonitoringExecution.TriggerSource.MANUAL;
        };
    }

    private boolean isConditionNode(Map<String, Object> config) {
        String type = String.valueOf(config.getOrDefault("type", "CONDITION")).toUpperCase(Locale.ROOT);
        return "CONDITION".equals(type) && config.containsKey("fieldPath") && config.containsKey("operator");
    }

    private boolean isConditionGroupNode(Map<String, Object> config) {
        return "GROUP".equals(String.valueOf(config.getOrDefault("type", "")).toUpperCase(Locale.ROOT))
                && config.containsKey("operator") && config.containsKey("children");
    }

    private ConditionResult evaluateCondition(Map<String, Object> config, Map<String, Object> context) {
        String nodeType = String.valueOf(config.getOrDefault("type", "CONDITION")).toUpperCase(Locale.ROOT);
        if ("GROUP".equals(nodeType)) {
            return evaluateConditionGroup(config, context);
        }
        if ("COUNT_MATCHING".equals(nodeType)) {
            return evaluateCountMatching(config, context);
        }
        if (!"CONDITION".equals(nodeType)) {
            throw new IllegalArgumentException("Unsupported condition node type: " + nodeType);
        }
        String fieldPath = requiredString(config, "fieldPath");
        String operator = requiredString(config, "operator").toUpperCase(Locale.ROOT);
        Object executionStatus = context.get("executionStatus");
        if (executionStatus != null && !"SUCCESS".equals(String.valueOf(executionStatus))
                && !List.of("executionStatus", "executionState", "errorMessage", "triggerSource", "resultStatus", "status", "message")
                        .contains(fieldPath)) {
            throw new IllegalArgumentException("Monitoring execution did not complete successfully; result-based condition was not evaluated.");
        }
        String resultStatus = String.valueOf(context.get("resultStatus")).toUpperCase(Locale.ROOT);
        String normalizedStatus = String.valueOf(context.get("status")).toUpperCase(Locale.ROOT);
        boolean resultFailed = List.of("FAILED", "FAILURE", "ERROR").contains(resultStatus)
                || List.of("FAILED", "FAILURE", "ERROR").contains(normalizedStatus);
        if (resultFailed
                && !List.of("executionStatus", "executionState", "errorMessage", "triggerSource", "resultStatus", "status", "message")
                        .contains(fieldPath)) {
            throw new IllegalArgumentException("Monitoring result reports failure; result-based condition was not evaluated.");
        }
        Object actual = resolvePath(context, fieldPath);
        if (actual == null && !operator.equals("EXISTS") && !operator.equals("NOT_EXISTS")) {
            throw new IllegalArgumentException("Configured field is missing from the monitoring result: " + fieldPath);
        }
        Object expected = config.containsKey("threshold") ? config.get("threshold") : config.get("value");

        boolean matched = switch (operator) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "GREATER_THAN", "GT" -> compareNumbers(actual, expected) > 0;
            case "GREATER_THAN_OR_EQUAL", "GTE" -> compareNumbers(actual, expected) >= 0;
            case "LESS_THAN", "LT" -> compareNumbers(actual, expected) < 0;
            case "LESS_THAN_OR_EQUAL", "LTE" -> compareNumbers(actual, expected) <= 0;
            case "EQUALS", "EQ" -> valuesEqual(actual, expected);
            case "NOT_EQUALS", "NE" -> !valuesEqual(actual, expected);
            case "EQUALS_IGNORE_CASE" -> actual != null && expected != null
                    && String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
            case "CONTAINS" -> contains(actual, expected);
            case "REGEX" -> regexMatches(actual, expected);
            case "IS_TRUE" -> actual instanceof Boolean value && value;
            case "IS_FALSE" -> actual instanceof Boolean value && !value;
            default -> throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        };
        return new ConditionResult(matched, actual);
    }

    /**
     * Counts records in a configured collection that match a field condition,
     * then compares that count to countThreshold. The collection must be an array
     * of objects; malformed data fails evaluation rather than silently counting zero.
     */
    private ConditionResult evaluateCountMatching(Map<String, Object> config, Map<String, Object> context) {
        String collectionPath = requiredString(config, "collectionPath");
        Object collectionValue = resolvePath(context, collectionPath);
        if (!(collectionValue instanceof List<?> records)) {
            throw new IllegalArgumentException("Count condition collection must resolve to an array: " + collectionPath);
        }

        String fieldPath = requiredString(config, "fieldPath");
        String fieldOperator = requiredString(config, "operator").toUpperCase(Locale.ROOT);
        Map<String, Object> rowCondition = new LinkedHashMap<>();
        rowCondition.put("type", "CONDITION");
        rowCondition.put("fieldPath", fieldPath);
        rowCondition.put("operator", fieldOperator);
        if (config.containsKey("threshold")) rowCondition.put("threshold", config.get("threshold"));
        if (config.containsKey("value")) rowCondition.put("value", config.get("value"));

        long matchingCount = 0;
        for (Object record : records) {
            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException("Count condition collection must contain only objects.");
            }
            Map<String, Object> rowContext = new LinkedHashMap<>(context);
            row.forEach((key, value) -> rowContext.put(String.valueOf(key), value));
            if (evaluateCondition(rowCondition, rowContext).matched()) matchingCount++;
        }

        Object countThreshold = config.get("countThreshold");
        if (countThreshold == null) {
            throw new IllegalArgumentException("Count condition requires 'countThreshold'.");
        }
        String countOperator = requiredString(config, "countOperator").toUpperCase(Locale.ROOT);
        boolean matched = switch (countOperator) {
            case "GREATER_THAN", "GT" -> matchingCount > compareCountThreshold(countThreshold);
            case "GREATER_THAN_OR_EQUAL", "GTE" -> matchingCount >= compareCountThreshold(countThreshold);
            case "LESS_THAN", "LT" -> matchingCount < compareCountThreshold(countThreshold);
            case "LESS_THAN_OR_EQUAL", "LTE" -> matchingCount <= compareCountThreshold(countThreshold);
            case "EQUALS", "EQ" -> matchingCount == compareCountThreshold(countThreshold);
            case "NOT_EQUALS", "NE" -> matchingCount != compareCountThreshold(countThreshold);
            default -> throw new IllegalArgumentException("Unsupported count comparison operator: " + countOperator);
        };
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("collectionPath", collectionPath);
        observed.put("matchingCount", matchingCount);
        observed.put("totalCount", records.size());
        return new ConditionResult(matched, observed);
    }

    private long compareCountThreshold(Object value) {
        try {
            long threshold = new BigDecimal(String.valueOf(value)).longValueExact();
            if (threshold < 0) throw new IllegalArgumentException("Count threshold cannot be negative.");
            return threshold;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Count threshold must be a non-negative whole number.");
        }
    }

    /**
     * Evaluates a nested condition expression. GROUP nodes contain an operator
     * (AND/OR) and a non-empty list of CONDITION or GROUP children. Legacy
     * single-condition JSON remains supported by the default CONDITION node.
     */
    private ConditionResult evaluateConditionGroup(Map<String, Object> config, Map<String, Object> context) {
        String operator = requiredString(config, "operator").toUpperCase(Locale.ROOT);
        if (!"AND".equals(operator) && !"OR".equals(operator)) {
            throw new IllegalArgumentException("Condition group operator must be AND or OR.");
        }
        Object rawChildren = config.get("children");
        if (!(rawChildren instanceof List<?> children) || children.isEmpty()) {
            throw new IllegalArgumentException("A condition group must contain at least one condition.");
        }

        boolean matched = "AND".equals(operator);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object child : children) {
            if (!(child instanceof Map<?, ?> childMap)) {
                throw new IllegalArgumentException("Each condition group child must be an object.");
            }
            Map<String, Object> childConfig = new LinkedHashMap<>();
            childMap.forEach((key, value) -> childConfig.put(String.valueOf(key), value));
            ConditionResult childResult = evaluateCondition(childConfig, context);
            results.add(Map.of(
                    "matched", childResult.matched(),
                    "value", childResult.value() == null ? "" : childResult.value()));
            if ("AND".equals(operator)) {
                matched = matched && childResult.matched();
            } else {
                matched = matched || childResult.matched();
            }
            // Do not short-circuit: evaluate every configured child so missing
            // fields and invalid operators cannot silently pass unnoticed.
        }
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("operator", operator);
        observed.put("children", results);
        return new ConditionResult(matched, observed);
    }

    private Object resolvePath(Map<String, Object> root, String fieldPath) {
        Object current = root;
        for (String part : fieldPath.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(part);
                    current = index >= 0 && index < list.size() ? list.get(index) : null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private int compareNumbers(Object actual, Object expected) {
        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Numeric condition requires both the field value and threshold.");
        }
        try {
            return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Numeric condition field and threshold must be valid numbers.");
        }
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        if (actual instanceof Number && expected instanceof Number) {
            return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected))) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private boolean contains(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        if (actual instanceof List<?> list) return list.stream().anyMatch(value -> valuesEqual(value, expected));
        return String.valueOf(actual).contains(String.valueOf(expected));
    }

    private boolean regexMatches(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        String regex = String.valueOf(expected);
        if (regex.length() > 256) throw new IllegalArgumentException("Regex condition cannot exceed 256 characters.");
        try {
            return Pattern.compile(regex).matcher(String.valueOf(actual)).find();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Regex condition is invalid.");
        }
    }

    private String correlationKey(Map<String, Object> condition, Map<String, Object> context) {
        Object configuredPath = condition.get("correlationFieldPath");
        if (configuredPath == null || String.valueOf(configuredPath).isBlank()) return "DEFAULT";
        Object value = resolvePath(context, String.valueOf(configuredPath));
        if (value == null) throw new IllegalArgumentException("Configured correlation field is missing from the result.");
        String key = value instanceof Map<?, ?> || value instanceof List<?> ? toJson(value) : String.valueOf(value);
        if (key.length() > 500) throw new IllegalArgumentException("Correlation key exceeds 500 characters.");
        return key;
    }

    private int previousConsecutiveCount(AlertRuleEvaluation previous, String phase, String countKey, String matchedKey) {
        if (previous == null || previous.getDetails() == null || previous.getDetails().isBlank()) return 0;
        try {
            Map<String, Object> details = objectMapper.readValue(previous.getDetails(),
                    new TypeReference<Map<String, Object>>() {});
            if (!phase.equals(details.get("phase")) || !Boolean.TRUE.equals(details.get(matchedKey))) return 0;
            Object count = details.get(countKey);
            return count instanceof Number number ? Math.max(0, number.intValue()) : 0;
        } catch (Exception exception) {
            return 0;
        }
    }

    private int consecutiveCount(Map<String, Object> config) {
        Object value = config.getOrDefault("consecutiveMatches", 1);
        try {
            int count = Integer.parseInt(String.valueOf(value));
            if (count < 1 || count > 100) throw new IllegalArgumentException("Consecutive match count must be between 1 and 100.");
            return count;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Consecutive match count must be a whole number.");
        }
    }

    private Map<String, Object> readConfig(String json, String label) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("The alert " + label + " configuration is invalid JSON.");
        }
    }

    private String requiredString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Alert configuration requires '" + key + "'.");
        }
        return String.valueOf(value).trim();
    }

    private String summary(AlertRule rule, Map<String, Object> condition, Object value) {
        return "Rule '" + rule.getName() + "' matched field '" + condition.get("fieldPath")
                + "' with observed value " + String.valueOf(value) + ".";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "null";
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Alert rule configuration is invalid.";
        String safe = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return safe.length() > 1000 ? safe.substring(0, 1000) : safe;
    }

    private record ConditionResult(boolean matched, Object value) {}
}
