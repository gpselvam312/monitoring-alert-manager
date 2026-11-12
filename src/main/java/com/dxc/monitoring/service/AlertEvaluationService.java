package com.dxc.monitoring.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.alert.condition.ConditionEvaluationContext;
import com.dxc.monitoring.alert.condition.ConditionEvaluationResult;
import com.dxc.monitoring.alert.condition.ConditionEvaluator;
import com.dxc.monitoring.alert.json.JsonDocument;
import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleEvaluation;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
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
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);
    private final MonitoringExecutionRepository executionRepository;
    private final MonitoringResultRepository resultRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertRuleEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;
    private final ConditionEvaluator conditionEvaluator;
    private final NotificationRoutingService notificationRoutingService;

    public AlertEvaluationService(MonitoringExecutionRepository executionRepository,
            MonitoringResultRepository resultRepository,
            AlertRuleRepository ruleRepository,
            AlertRepository alertRepository,
            AlertHistoryRepository historyRepository,
            AlertRuleEvaluationRepository evaluationRepository,
            ObjectMapper objectMapper, ConditionEvaluator conditionEvaluator,
            NotificationRoutingService notificationRoutingService) {
        this.executionRepository = executionRepository;
        this.resultRepository = resultRepository;
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.evaluationRepository = evaluationRepository;
        this.objectMapper = objectMapper;
        this.conditionEvaluator = conditionEvaluator;
        this.notificationRoutingService = notificationRoutingService;
    }

    @Transactional
    public void evaluateExecution(Long executionId) {
        MonitoringExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring execution not found."));
        if (execution.getStatus() == MonitoringExecution.ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Cannot evaluate an execution that is still running.");
        }

        log.debug("Evaluating alert rules for executionId={}, jobId={}, status={}, triggerSource={}",
                executionId, execution.getMonitoringJob().getId(), execution.getStatus(), execution.getTriggerSource());
        Map<String, Object> context = buildContext(execution);
        JsonDocument document = JsonDocument.from(context.get("response"));
        List<AlertRule> rules = ruleRepository.findEnabledForJobForUpdate(execution.getMonitoringJob().getId());
        log.debug("Found {} enabled alert rules for executionId={}, jobId={}", rules.size(), executionId,
                execution.getMonitoringJob().getId());
        for (AlertRule rule : rules) {
            if (!sourceAllowed(rule, execution.getTriggerSource())) {
                log.debug("Skipping alert rule id={} name={} for executionId={} due to execution-source policy",
                        rule.getId(), rule.getName(), executionId);
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
                evaluateRule(rule, execution, context, document, previousEvaluation.orElse(null));
            } catch (IllegalArgumentException exception) {
                saveEvaluation(rule, execution, AlertRuleEvaluation.Outcome.ERROR,
                        Map.of("error", safeMessage(exception), "phase", "VALIDATION"));
            }
        }
    }

    private void evaluateRule(AlertRule rule, MonitoringExecution execution, Map<String, Object> context,
            JsonDocument document, AlertRuleEvaluation previousEvaluation) {
        Map<String, Object> condition = readConfig(rule.getConditionConfig(), "condition");
        String correlationKey = correlationKey(condition, context);
        Optional<Alert> active = alertRepository.findByAlertRuleIdAndCorrelationKeyAndStatus(
                rule.getId(), correlationKey, Alert.AlertStatus.ACTIVE);
        ConditionResult conditionResult;
        if (active.isEmpty()) {
            conditionResult = evaluateCondition(condition, context, document);
        } else {
            // Recovery criteria are independent. Missing condition data must not
            // prevent a configured NOT_EXISTS recovery condition from resolving an alert.
            try {
                conditionResult = evaluateCondition(condition, context, document);
            } catch (IllegalArgumentException exception) {
                conditionResult = new ConditionResult(false, null, Map.of());
            }
        }

        Map<String, Object> evaluationDetails = new LinkedHashMap<>();
        evaluationDetails.put("phase", active.isPresent() ? "RECOVERY" : "CONDITION");
        evaluationDetails.put("fieldPath", condition.get("fieldPath"));
        evaluationDetails.put("conditionMatched", conditionResult.matched());
        evaluationDetails.put("observedValue", conditionResult.value());
        evaluationDetails.put("conditionDetails", conditionResult.details());

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
            ConditionResult recoveryResult = evaluateCondition(recovery, context, document);
            recoveryMatched = recoveryResult.matched();
            recoveryValue = recoveryResult.value();
            evaluationDetails.put("recoveryDetails", recoveryResult.details());
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
        Map<String, Object> condition = readConfig(rule.getConditionConfig(), "condition");
        String subjectTemplate = rule.getAlertSubject();
        if (subjectTemplate == null || subjectTemplate.isBlank()) subjectTemplate = "[{severity}] {ruleName}";
        String messageTemplate = rule.getAlertMessage();
        if (messageTemplate == null || messageTemplate.isBlank()) {
            messageTemplate = "Job {jobName} in application {applicationName}, environment {environment}: "
                    + "{fieldPath} evaluated to {actualValue} (threshold: {threshold}).";
        }
        alert.setTitle(truncate(renderAlertTemplate(subjectTemplate, rule, execution, condition,
                result.value(), now), 500));
        alert.setSummary(renderAlertTemplate(messageTemplate, rule, execution, condition,
                result.value(), now));
        alert.setCurrentValue(toJson(result.value()));
        alert.setFirstOccurredAt(now);
        alert.setLastOccurredAt(now);
        alert.setOccurrenceCount(1L);
        alert.setLastExecution(execution);
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alert = alertRepository.save(alert);
        log.info("Created active alert id={} ruleId={} jobId={} severity={} executionId={}",
                alert.getId(), rule.getId(), execution.getMonitoringJob().getId(), alert.getSeverity(), execution.getId());
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
        log.debug("Saved alert history id={} alertId={} eventType={}; starting notification routing",
                savedHistory.getId(), alert.getId(), eventType);
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
        if (results.isEmpty()) return context;

        MonitoringResult result = results.get(results.size() - 1);
        context.put("resultStatus", result.getStatus() == null ? null : result.getStatus().name());
        context.put("resultValue", result.getValue());
        context.put("resultUnit", result.getUnit());

        // Keep evaluation consistent with field discovery: evaluate the actual response
        // payload first, not a generic success message that may hide the JSON response.
        Object payload = parseStructuredPayload(result.getResultData());
        if (payload == null) payload = parseStructuredPayload(result.getRawOutput());
        if (payload == null) payload = parseStructuredPayload(result.getMessage());
        // Namespaced paths are additive: keep the legacy top-level payload view
        // while exposing the complete response and explicit data namespace.
        context.put("response", payload);
        Object dataView = payload instanceof Map<?, ?> responseMap && responseMap.containsKey("data")
                ? responseMap.get("data") : payload;
        context.put("data", dataView);

        boolean normalizedEnvelope = isNormalizedEnvelope(payload);
        payload = unwrapNormalizedPayload(payload);

        boolean payloadFound = payload != null;
        if (payload instanceof Map<?, ?> map) {
            map.forEach((key, value) -> context.put(String.valueOf(key), value));
            // Keep legacy rules using data.<path> working when the normalized
            // result wrapper is removed for discovery/evaluation consistency.
            if (normalizedEnvelope) context.put("data", payload);
        } else if (payload != null) {
            context.put("data", payload);
        } else {
            String textPayload = result.getRawOutput();
            if (textPayload == null || textPayload.isBlank()) textPayload = result.getMessage();
            if (textPayload != null && !textPayload.isBlank()) {
                Map<String, Object> textFields = parseKeyValuePayload(textPayload);
                if (!textFields.isEmpty()) {
                    context.put("response", textFields);
                    context.put("data", textFields);
                    context.putAll(textFields);
                    payloadFound = true;
                } else {
                    Map<String, Object> textResponse = Map.of("message", textPayload);
                    context.put("response", textResponse);
                    context.put("data", textResponse);
                    context.put("message", textPayload);
                }
            }
        }

        if (!payloadFound && execution.getStatus() == MonitoringExecution.ExecutionStatus.SUCCESS) {
            // No structured result is not equivalent to a healthy value.
            context.put("resultDataMissing", true);
        }
        return context;
    }

    private boolean isNormalizedEnvelope(Object parsed) {
        return parsed instanceof Map<?, ?> map
                && map.containsKey("schemaVersion")
                && map.containsKey("data");
    }

    private Object unwrapNormalizedPayload(Object parsed) {
        return isNormalizedEnvelope(parsed) ? ((Map<?, ?>) parsed).get("data") : parsed;
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
        return switch (type) {
            case "CONDITION" -> config.containsKey("fieldPath") && config.containsKey("operator");
            case "FIELD_COMPARISON" -> config.containsKey("leftPath")
                    && config.containsKey("rightPath") && config.containsKey("operator");
            case "COLLECTION" -> config.containsKey("collectionPath")
                    && config.containsKey("quantifier") && config.containsKey("condition");
            case "AGGREGATE" -> config.containsKey("collectionPath")
                    && config.containsKey("operation") && config.containsKey("operator");
            case "COUNT_TOTAL" -> config.containsKey("collectionPath")
                    && config.containsKey("countOperator") && config.containsKey("countThreshold");
            case "COUNT_MATCHING" -> config.containsKey("collectionPath")
                    && config.containsKey("fieldPath") && config.containsKey("operator");
            default -> false;
        };
    }

    private boolean isConditionGroupNode(Map<String, Object> config) {
        return "GROUP".equals(String.valueOf(config.getOrDefault("type", "")).toUpperCase(Locale.ROOT))
                && config.containsKey("operator") && config.containsKey("children");
    }

    private ConditionResult evaluateCondition(Map<String, Object> config,
            Map<String, Object> context, JsonDocument document) {
        ConditionEvaluationResult result = conditionEvaluator.evaluate(config,
                new ConditionEvaluationContext(document, context));
        return new ConditionResult(result.matched(), result.observedValue(), result.details());
    }

    private Object resolvePath(Map<String, Object> context, String fieldPath) {
        ConditionEvaluationContext.ResolvedPath resolved =
                new ConditionEvaluationContext(JsonDocument.from(context.get("response")), context).resolve(fieldPath);
        if (!resolved.found()) return null;
        return resolved.values().size() == 1 ? resolved.values().get(0) : resolved.values();
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

    private String renderAlertTemplate(String template, AlertRule rule, MonitoringExecution execution,
            Map<String, Object> condition, Object observedValue, OffsetDateTime timestamp) {
        Map<String, String> values = new LinkedHashMap<>();
        MonitoringJob job = execution.getMonitoringJob();
        values.put("ruleName", safeTemplateValue(rule.getName()));
        values.put("severity", rule.getSeverity().name());
        values.put("jobName", safeTemplateValue(job == null ? "" : job.getName()));
        values.put("applicationName", safeTemplateValue(job == null || job.getApplication() == null
                ? "" : job.getApplication().getName()));
        values.put("environment", safeTemplateValue(job == null || job.getEnvironment() == null
                ? "—" : job.getEnvironment().getName()));
        values.put("fieldPath", safeTemplateValue(findConfiguredValue(condition, "fieldPath", "collectionPath", "leftPath", "valuePath")));
        values.put("actualValue", safeTemplateValue(observedValue instanceof Map<?, ?> || observedValue instanceof List<?>
                ? toJson(observedValue) : String.valueOf(observedValue == null ? "null" : observedValue)));
        values.put("threshold", safeTemplateValue(findConfiguredValue(condition, "threshold", "value", "countThreshold")));
        values.put("executionId", String.valueOf(execution.getId()));
        values.put("timestamp", timestamp.toString());

        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private String findConfiguredValue(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            Object value = node.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        Object childrenValue = node.get("children");
        if (childrenValue instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    Map<String, Object> childNode = new LinkedHashMap<>();
                    childMap.forEach((key, value) -> childNode.put(String.valueOf(key), value));
                    String found = findConfiguredValue(childNode, keys);
                    if (!found.isBlank()) return found;
                }
            }
        }
        return "";
    }

    private String safeTemplateValue(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String summary(AlertRule rule, Map<String, Object> condition, Object value) {
        String type = String.valueOf(condition.getOrDefault("type", "CONDITION")).toUpperCase(Locale.ROOT);
        if ("COUNT_TOTAL".equals(type)) {
            return "Rule '" + rule.getName() + "' matched total record count for collection '"
                    + condition.get("collectionPath") + "' with observed count " + observedCount(value) + ".";
        }
        if ("COUNT_MATCHING".equals(type)) {
            return "Rule '" + rule.getName() + "' matched record count for collection '"
                    + condition.get("collectionPath") + "' with observed matching count " + observedCount(value) + ".";
        }
        if ("GROUP".equals(type)) {
            return "Rule '" + rule.getName() + "' matched its configured condition group.";
        }
        if ("COLLECTION".equals(type)) {
            return "Rule '" + rule.getName() + "' matched a collection condition on '" + condition.get("collectionPath") + "'.";
        }
        if ("AGGREGATE".equals(type)) {
            return "Rule '" + rule.getName() + "' matched " + condition.get("operation")
                    + " aggregate for collection '" + condition.get("collectionPath") + "'.";
        }
        if ("FIELD_COMPARISON".equals(type)) {
            return "Rule '" + rule.getName() + "' matched a comparison between '"
                    + condition.get("leftPath") + "' and '" + condition.get("rightPath") + "'.";
        }
        return "Rule '" + rule.getName() + "' matched field '" + condition.get("fieldPath")
                + "' with observed value " + String.valueOf(value) + ".";
    }

    private Object observedCount(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object matching = map.get("matchingCount");
            if (matching != null) return matching;
            Object total = map.get("totalCount");
            return total == null ? "unknown" : total;
        }
        return value;
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

    private record ConditionResult(boolean matched, Object value, Map<String, Object> details) {}
}
