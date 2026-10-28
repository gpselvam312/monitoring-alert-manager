package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class AlertRetentionCleanupExecutor {
    private final AlertRetentionPolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AlertRetentionCleanupExecutor(AlertRetentionPolicyRepository policyRepository,
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Preview preview(Long policyId) {
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Retention policy was not found."));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(policy.getRetentionDays());
        long candidates = countCandidates(policy.getDataType(), cutoff);
        return new Preview(policy.getDataType(), policy.getRetentionDays(), cutoff, candidates);
    }

    @Transactional
    public CleanupResult cleanup(Long policyId) {
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .filter(AlertRetentionPolicy::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Select an enabled retention policy before running cleanup."));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(policy.getRetentionDays());
        OffsetDateTime cutoffTimestamp = cutoff;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dataType", policy.getDataType().name());
        detail.put("retentionDays", policy.getRetentionDays());
        detail.put("cutoff", cutoff.toString());

        long examined;
        long deleted;
        switch (policy.getDataType()) {
            case RESOLVED_ALERTS -> {
                String eligibleAlerts = eligibleResolvedAlertsSql();
                long alerts = count("SELECT COUNT(*) FROM (" + eligibleAlerts + ") eligible", cutoffTimestamp);
                long deliveries = count("SELECT COUNT(*) FROM ra_fcb.notification_deliveries d WHERE d.alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                long histories = count("SELECT COUNT(*) FROM ra_fcb.alert_history h WHERE h.alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                long readStates = count("SELECT COUNT(*) FROM ra_fcb.user_alert_states s WHERE s.alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                examined = alerts + deliveries + histories + readStates;
                int deletedDeliveries = jdbcTemplate.update("DELETE FROM ra_fcb.notification_deliveries WHERE alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                int deletedReadStates = jdbcTemplate.update("DELETE FROM ra_fcb.user_alert_states WHERE alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                int deletedHistories = jdbcTemplate.update("DELETE FROM ra_fcb.alert_history WHERE alert_id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                int deletedAlerts = jdbcTemplate.update("DELETE FROM ra_fcb.alerts WHERE id IN (" + eligibleAlerts + ")", cutoffTimestamp);
                deleted = (long) deletedDeliveries + deletedReadStates + deletedHistories + deletedAlerts;
                detail.put("alertsDeleted", deletedAlerts);
                detail.put("historyRowsDeleted", deletedHistories);
                detail.put("deliveryRowsDeleted", deletedDeliveries);
                detail.put("userReadStatesDeleted", deletedReadStates);
            }
            case ALERT_HISTORY -> {
                String eligibleHistory = eligibleResolvedHistorySql();
                examined = count("SELECT COUNT(*) FROM (" + eligibleHistory + ") eligible", cutoffTimestamp);
                deleted = jdbcTemplate.update("DELETE FROM ra_fcb.alert_history WHERE id IN (" + eligibleHistory + ")", cutoffTimestamp);
                detail.put("historyRowsDeleted", deleted);
            }
            case NOTIFICATION_DELIVERIES -> {
                String eligibleDeliveries = "SELECT id FROM ra_fcb.notification_deliveries WHERE created_at < ? AND (status IN ('SENT', 'SKIPPED') OR (status = 'FAILED' AND attempt_count >= 8))";
                examined = count("SELECT COUNT(*) FROM (" + eligibleDeliveries + ") eligible", cutoffTimestamp);
                deleted = jdbcTemplate.update("DELETE FROM ra_fcb.notification_deliveries WHERE id IN (" + eligibleDeliveries + ")", cutoffTimestamp);
                detail.put("deliveryRowsDeleted", deleted);
            }
            case RULE_EVALUATIONS -> {
                String eligibleEvaluations = eligibleOldEvaluationsSql();
                examined = count("SELECT COUNT(*) FROM (" + eligibleEvaluations + ") eligible", cutoffTimestamp);
                deleted = jdbcTemplate.update("DELETE FROM ra_fcb.alert_rule_evaluations WHERE id IN (" + eligibleEvaluations + ")", cutoffTimestamp);
                detail.put("evaluationRowsDeleted", deleted);
            }
            default -> throw new IllegalArgumentException("Unsupported retention data type.");
        }
        detail.put("recordsExamined", examined);
        detail.put("recordsDeleted", deleted);
        return new CleanupResult(examined, deleted, toJson(detail));
    }

    private long countCandidates(AlertRetentionPolicy.DataType dataType, OffsetDateTime cutoff) {
        String sql = switch (dataType) {
            case RESOLVED_ALERTS -> "SELECT COUNT(*) FROM (" + eligibleResolvedAlertsSql() + ") eligible";
            case ALERT_HISTORY -> "SELECT COUNT(*) FROM (" + eligibleResolvedHistorySql() + ") eligible";
            case NOTIFICATION_DELIVERIES -> "SELECT COUNT(*) FROM ra_fcb.notification_deliveries WHERE created_at < ? AND status NOT IN ('PENDING', 'SENDING')";
            case RULE_EVALUATIONS -> "SELECT COUNT(*) FROM (" + eligibleOldEvaluationsSql() + ") eligible";
        };
        return count(sql, cutoff);
    }

    private String eligibleResolvedAlertsSql() {
        return """
                SELECT a.id
                FROM ra_fcb.alerts a
                WHERE a.status = 'RESOLVED'
                  AND a.resolved_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM ra_fcb.notification_deliveries d
                      WHERE d.alert_id = a.id AND (d.status IN ('PENDING', 'SENDING') OR (d.status = 'FAILED' AND d.attempt_count < 8))
                  )
                """;
    }

    private String eligibleResolvedHistorySql() {
        return """
                SELECT h.id
                FROM ra_fcb.alert_history h
                JOIN ra_fcb.alerts a ON a.id = h.alert_id
                WHERE a.status = 'RESOLVED'
                  AND h.occurred_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM ra_fcb.notification_deliveries d
                      WHERE d.alert_history_id = h.id
                  )
                """;
    }

    private String eligibleOldEvaluationsSql() {
        return """
                SELECT e.id
                FROM ra_fcb.alert_rule_evaluations e
                WHERE e.evaluated_at < ?
                  AND EXISTS (
                      SELECT 1
                      FROM ra_fcb.alert_rule_evaluations newer
                      WHERE newer.alert_rule_id = e.alert_rule_id
                        AND (newer.evaluated_at > e.evaluated_at
                          OR (newer.evaluated_at = e.evaluated_at AND newer.id > e.id))
                  )
                """;
    }

    private long count(String sql, Object parameter) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameter);
        return value == null ? 0L : value;
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Retention audit details could not be serialized."); }
    }

    public record Preview(AlertRetentionPolicy.DataType dataType, int retentionDays,
            OffsetDateTime cutoff, long candidateCount) {}
    public record CleanupResult(long recordsExamined, long recordsDeleted, String details) {}
}
