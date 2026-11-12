package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rule_evaluations", schema = "ra_fcb")
public class AlertRuleEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private MonitoringExecution execution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String details = "{}";

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) evaluatedAt = OffsetDateTime.now();
        if (details == null || details.isBlank()) details = "{}";
    }

    public enum Outcome { MATCHED, NO_MATCH, SKIPPED, ERROR }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AlertRule getAlertRule() { return alertRule; }
    public void setAlertRule(AlertRule alertRule) { this.alertRule = alertRule; }
    public MonitoringExecution getExecution() { return execution; }
    public void setExecution(MonitoringExecution execution) { this.execution = execution; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(OffsetDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
