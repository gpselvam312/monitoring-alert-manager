package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity
@Table(name = "alert_cleanup_runs", schema = "ra_fcb")
public class AlertCleanupRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id")
    private AlertRetentionPolicy policy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private RunStatus status;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Column(name = "records_examined", nullable = false)
    private Long recordsExamined = 0L;
    @Column(name = "records_deleted", nullable = false)
    private Long recordsDeleted = 0L;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private String details = "{}";
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "requested_by")
    private Long requestedBy;
    public enum RunStatus { RUNNING, COMPLETED, FAILED, CANCELLED }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public AlertRetentionPolicy getPolicy(){return policy;} public void setPolicy(AlertRetentionPolicy v){policy=v;}
    public RunStatus getStatus(){return status;} public void setStatus(RunStatus v){status=v;}
    public OffsetDateTime getStartedAt(){return startedAt;} public void setStartedAt(OffsetDateTime v){startedAt=v;}
    public OffsetDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(OffsetDateTime v){completedAt=v;}
    public Long getRecordsExamined(){return recordsExamined;} public void setRecordsExamined(Long v){recordsExamined=v;}
    public Long getRecordsDeleted(){return recordsDeleted;} public void setRecordsDeleted(Long v){recordsDeleted=v;}
    public String getDetails(){return details;} public void setDetails(String v){details=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public Long getRequestedBy(){return requestedBy;} public void setRequestedBy(Long v){requestedBy=v;}
}
