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
@Table(name = "alert_history", schema = "ra_fcb")
public class AlertHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private Alert.AlertStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private Alert.AlertStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_severity", length = 20)
    private AlertRule.Severity previousSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_severity", length = 20)
    private AlertRule.Severity newSeverity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private MonitoringExecution execution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observed_value", columnDefinition = "jsonb")
    private String observedValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String details = "{}";

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
        if (details == null || details.isBlank()) details = "{}";
    }

    public enum EventType {
        CREATED, OCCURRENCE, SEVERITY_CHANGED, RESOLVED,
        NOTIFICATION_QUEUED, NOTIFICATION_RESULT, RULE_EVALUATION_ERROR
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Alert getAlert() { return alert; }
    public void setAlert(Alert alert) { this.alert = alert; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public Alert.AlertStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(Alert.AlertStatus previousStatus) { this.previousStatus = previousStatus; }
    public Alert.AlertStatus getNewStatus() { return newStatus; }
    public void setNewStatus(Alert.AlertStatus newStatus) { this.newStatus = newStatus; }
    public AlertRule.Severity getPreviousSeverity() { return previousSeverity; }
    public void setPreviousSeverity(AlertRule.Severity previousSeverity) { this.previousSeverity = previousSeverity; }
    public AlertRule.Severity getNewSeverity() { return newSeverity; }
    public void setNewSeverity(AlertRule.Severity newSeverity) { this.newSeverity = newSeverity; }
    public MonitoringExecution getExecution() { return execution; }
    public void setExecution(MonitoringExecution execution) { this.execution = execution; }
    public String getObservedValue() { return observedValue; }
    public void setObservedValue(String observedValue) { this.observedValue = observedValue; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
