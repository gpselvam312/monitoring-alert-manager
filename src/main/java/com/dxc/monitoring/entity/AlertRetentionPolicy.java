package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "alert_retention_policies", schema = "ra_fcb")
public class AlertRetentionPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "data_type", nullable = false, length = 40, unique = true)
    private DataType dataType;
    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;
    @Column(nullable = false)
    private boolean enabled = false;
    @Column(length = 1000)
    private String description;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "updated_by")
    private Long updatedBy;
    @PrePersist protected void onCreate(){OffsetDateTime n=OffsetDateTime.now();if(createdAt==null)createdAt=n;if(updatedAt==null)updatedAt=n;}
    @PreUpdate protected void onUpdate(){updatedAt=OffsetDateTime.now();}
    public enum DataType { RESOLVED_ALERTS, ALERT_HISTORY, NOTIFICATION_DELIVERIES, RULE_EVALUATIONS }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public DataType getDataType(){return dataType;} public void setDataType(DataType v){dataType=v;}
    public Integer getRetentionDays(){return retentionDays;} public void setRetentionDays(Integer v){retentionDays=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;}
}
