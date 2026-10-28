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
@Table(name = "alert_rules", schema = "ra_fcb")
public class AlertRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitoring_job_id", nullable = false)
    private MonitoringJob monitoringJob;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity = Severity.CRITICAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_source_policy", nullable = false, length = 30)
    private ExecutionSourcePolicy executionSourcePolicy = ExecutionSourcePolicy.SCHEDULED_ONLY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_config", nullable = false, columnDefinition = "jsonb")
    private String conditionConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recovery_config", nullable = false, columnDefinition = "jsonb")
    private String recoveryConfig = "{}";

    @Column(name = "notification_policy_id")
    private Long notificationPolicyId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (recoveryConfig == null || recoveryConfig.isBlank()) recoveryConfig = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum Severity { INFO, WARNING, CRITICAL }
    public enum ExecutionSourcePolicy { SCHEDULED_ONLY, MANUAL_ONLY, ALL_NON_STREAMING }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MonitoringJob getMonitoringJob() { return monitoringJob; }
    public void setMonitoringJob(MonitoringJob monitoringJob) { this.monitoringJob = monitoringJob; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public ExecutionSourcePolicy getExecutionSourcePolicy() { return executionSourcePolicy; }
    public void setExecutionSourcePolicy(ExecutionSourcePolicy executionSourcePolicy) { this.executionSourcePolicy = executionSourcePolicy; }
    public String getConditionConfig() { return conditionConfig; }
    public void setConditionConfig(String conditionConfig) { this.conditionConfig = conditionConfig; }
    public String getRecoveryConfig() { return recoveryConfig; }
    public void setRecoveryConfig(String recoveryConfig) { this.recoveryConfig = recoveryConfig; }
    public Long getNotificationPolicyId() { return notificationPolicyId; }
    public void setNotificationPolicyId(Long notificationPolicyId) { this.notificationPolicyId = notificationPolicyId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
