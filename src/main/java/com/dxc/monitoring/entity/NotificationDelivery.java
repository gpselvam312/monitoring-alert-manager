package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_deliveries", schema = "ra_fcb")
public class NotificationDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "alert_history_id", nullable = false)
    private AlertHistory alertHistory;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "channel_id", nullable = false)
    private NotificationChannel channel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_id")
    private NotificationRecipient recipient;
    @Column(name = "destination_snapshot", nullable = false, length = 1000)
    private String destinationSnapshot;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 500)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PENDING;
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;
    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;
    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    @Column(name = "provider_message_id", length = 500)
    private String providerMessageId;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "message_payload", nullable = false, columnDefinition = "jsonb")
    private String messagePayload = "{}";
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;
    @PrePersist protected void onCreate(){OffsetDateTime n=OffsetDateTime.now();if(nextAttemptAt==null)nextAttemptAt=n;if(createdAt==null)createdAt=n;if(updatedAt==null)updatedAt=n;if(attemptCount==null)attemptCount=0;if(messagePayload==null)messagePayload="{}";}
    @PreUpdate protected void onUpdate(){updatedAt=OffsetDateTime.now();}
    public enum DeliveryStatus { PENDING, SENDING, SENT, FAILED, SKIPPED }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Alert getAlert(){return alert;} public void setAlert(Alert v){alert=v;}
    public AlertHistory getAlertHistory(){return alertHistory;} public void setAlertHistory(AlertHistory v){alertHistory=v;}
    public NotificationChannel getChannel(){return channel;} public void setChannel(NotificationChannel v){channel=v;}
    public NotificationRecipient getRecipient(){return recipient;} public void setRecipient(NotificationRecipient v){recipient=v;}
    public String getDestinationSnapshot(){return destinationSnapshot;} public void setDestinationSnapshot(String v){destinationSnapshot=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public DeliveryStatus getStatus(){return status;} public void setStatus(DeliveryStatus v){status=v;}
    public Integer getAttemptCount(){return attemptCount;} public void setAttemptCount(Integer v){attemptCount=v;}
    public OffsetDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(OffsetDateTime v){nextAttemptAt=v;}
    public OffsetDateTime getLastAttemptAt(){return lastAttemptAt;} public void setLastAttemptAt(OffsetDateTime v){lastAttemptAt=v;}
    public OffsetDateTime getSentAt(){return sentAt;} public void setSentAt(OffsetDateTime v){sentAt=v;}
    public String getProviderMessageId(){return providerMessageId;} public void setProviderMessageId(String v){providerMessageId=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
    public String getMessagePayload(){return messagePayload;} public void setMessagePayload(String v){messagePayload=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public OffsetDateTime getLockedAt(){return lockedAt;} public void setLockedAt(OffsetDateTime v){lockedAt=v;}
}
