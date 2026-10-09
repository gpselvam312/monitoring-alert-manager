package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "streaming_job_claims", schema = "ra_fcb")
public class StreamingJobClaim
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitoring_job_id", nullable = false, unique = true)
    private MonitoringJob monitoringJob;

    @Column(name = "execution_id")
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status = ClaimStatus.IDLE;

    @Column(name = "owner_instance_id", length = 100)
    private String ownerInstanceId;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "process_start_time")
    private OffsetDateTime processStartTime;

    @Column(name = "remote_pid", length = 100)
    private String remotePid;

    @Column(name = "started_by", length = 100)
    private String startedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "heartbeat_at")
    private OffsetDateTime heartbeatAt;

    @Column(name = "deadline_at")
    private OffsetDateTime deadlineAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "output_buffer", nullable = false, columnDefinition = "text")
    private String outputBuffer = "";

    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ClaimStatus
    {
        IDLE, STARTING, RUNNING, STOPPING, COMPLETED, FAILED, TIMED_OUT, STOPPED, RECOVERY_REQUIRED;

        public boolean isActive()
        {
            return this == STARTING || this == RUNNING || this == STOPPING || this == RECOVERY_REQUIRED;
        }
    }

    public Long getId() { return id; }
    public MonitoringJob getMonitoringJob() { return monitoringJob; }
    public void setMonitoringJob(MonitoringJob monitoringJob) { this.monitoringJob = monitoringJob; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public String getOwnerInstanceId() { return ownerInstanceId; }
    public void setOwnerInstanceId(String ownerInstanceId) { this.ownerInstanceId = ownerInstanceId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public OffsetDateTime getProcessStartTime() { return processStartTime; }
    public void setProcessStartTime(OffsetDateTime processStartTime) { this.processStartTime = processStartTime; }
    public String getRemotePid() { return remotePid; }
    public void setRemotePid(String remotePid) { this.remotePid = remotePid; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(OffsetDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public OffsetDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(OffsetDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getOutputBuffer() { return outputBuffer; }
    public void setOutputBuffer(String outputBuffer) { this.outputBuffer = outputBuffer; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
