package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_routes", schema = "ra_fcb")
public class NotificationRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "notification_policy_id", nullable = false)
    private NotificationPolicy notificationPolicy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "channel_id", nullable = false)
    private NotificationChannel channel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_id")
    private NotificationRecipient recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_id")
    private NotificationGroup group;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AlertRule.Severity severity;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "minimum_interval_seconds", nullable = false)
    private Integer minimumIntervalSeconds = 0;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @PrePersist protected void onCreate(){OffsetDateTime n=OffsetDateTime.now();if(createdAt==null)createdAt=n;if(updatedAt==null)updatedAt=n;if(minimumIntervalSeconds==null)minimumIntervalSeconds=0;}
    @PreUpdate protected void onUpdate(){updatedAt=OffsetDateTime.now();}
    public enum EventType { CREATED, RECURRENCE, ESCALATED, RECOVERED, REMINDER }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public NotificationPolicy getNotificationPolicy(){return notificationPolicy;} public void setNotificationPolicy(NotificationPolicy v){notificationPolicy=v;}
    public NotificationChannel getChannel(){return channel;} public void setChannel(NotificationChannel v){channel=v;}
    public NotificationRecipient getRecipient(){return recipient;} public void setRecipient(NotificationRecipient v){recipient=v;}
    public NotificationGroup getGroup(){return group;} public void setGroup(NotificationGroup v){group=v;}
    public AlertRule.Severity getSeverity(){return severity;} public void setSeverity(AlertRule.Severity v){severity=v;}
    public EventType getEventType(){return eventType;} public void setEventType(EventType v){eventType=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public Integer getMinimumIntervalSeconds(){return minimumIntervalSeconds;} public void setMinimumIntervalSeconds(Integer v){minimumIntervalSeconds=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}
