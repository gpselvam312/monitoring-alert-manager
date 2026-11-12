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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "alerts", schema = "ra_fcb")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitoring_job_id", nullable = false)
    private MonitoringJob monitoringJob;

    @Column(name = "correlation_key", nullable = false, length = 500)
    private String correlationKey = "DEFAULT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertRule.Severity severity;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_value", columnDefinition = "jsonb")
    private String currentValue;

    @Column(name = "first_occurred_at", nullable = false)
    private OffsetDateTime firstOccurredAt;

    @Column(name = "last_occurred_at", nullable = false)
    private OffsetDateTime lastOccurredAt;

    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_execution_id", nullable = false)
    private MonitoringExecution lastExecution;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (firstOccurredAt == null) firstOccurredAt = now;
        if (lastOccurredAt == null) lastOccurredAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (occurrenceCount == null || occurrenceCount < 1) occurrenceCount = 1L;
        if (correlationKey == null || correlationKey.isBlank()) correlationKey = "DEFAULT";
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public enum AlertStatus { ACTIVE, RESOLVED }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AlertRule getAlertRule() { return alertRule; }
    public void setAlertRule(AlertRule alertRule) { this.alertRule = alertRule; }
    public MonitoringJob getMonitoringJob() { return monitoringJob; }
    public void setMonitoringJob(MonitoringJob monitoringJob) { this.monitoringJob = monitoringJob; }
    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }
    public AlertRule.Severity getSeverity() { return severity; }
    public void setSeverity(AlertRule.Severity severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCurrentValue() { return currentValue; }
    public void setCurrentValue(String currentValue) { this.currentValue = currentValue; }
    public OffsetDateTime getFirstOccurredAt() { return firstOccurredAt; }
    public void setFirstOccurredAt(OffsetDateTime firstOccurredAt) { this.firstOccurredAt = firstOccurredAt; }
    public OffsetDateTime getLastOccurredAt() { return lastOccurredAt; }
    public void setLastOccurredAt(OffsetDateTime lastOccurredAt) { this.lastOccurredAt = lastOccurredAt; }
    public Long getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public MonitoringExecution getLastExecution() { return lastExecution; }
    public void setLastExecution(MonitoringExecution lastExecution) { this.lastExecution = lastExecution; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
