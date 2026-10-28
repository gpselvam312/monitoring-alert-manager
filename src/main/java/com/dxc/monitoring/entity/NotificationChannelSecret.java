package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_channel_secrets", schema = "ra_fcb")
public class NotificationChannelSecret {
    @EmbeddedId private NotificationChannelSecretId id;
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "text")
    private String encryptedValue;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "updated_by")
    private Long updatedBy;
    @PrePersist @PreUpdate protected void onWrite(){updatedAt=OffsetDateTime.now();}
    public NotificationChannelSecretId getId(){return id;} public void setId(NotificationChannelSecretId v){id=v;}
    public String getEncryptedValue(){return encryptedValue;} public void setEncryptedValue(String v){encryptedValue=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;}
}
