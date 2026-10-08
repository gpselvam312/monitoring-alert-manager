package com.dxc.monitoring.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "monitoring_executions",
    schema = "ra_fcb",
    indexes = {
        @Index(name = "idx_monitoring_executions_job", columnList = "monitoring_job_id"),
        @Index(name = "idx_monitoring_executions_started_at", columnList = "started_at"),
        @Index(name = "idx_monitoring_executions_status", columnList = "status")
    }
)
public class MonitoringExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitoring_job_id", nullable = false)
    private MonitoringJob monitoringJob;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum ExecutionStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        TIMEOUT,
        ERROR
    }

    public Long getId() {
        return id;
    }

    public MonitoringJob getMonitoringJob() {
        return monitoringJob;
    }

    public void setMonitoringJob(MonitoringJob monitoringJob) {
        this.monitoringJob = monitoringJob;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}