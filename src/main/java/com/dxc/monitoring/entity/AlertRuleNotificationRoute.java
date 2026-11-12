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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rule_notification_routes", schema = "ra_fcb")
public class AlertRuleNotificationRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private NotificationChannel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private NotificationRecipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private NotificationGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertRule.Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private NotificationRoute.EventType eventType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "minimum_interval_seconds", nullable = false)
    private Integer minimumIntervalSeconds = 300;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (minimumIntervalSeconds == null) minimumIntervalSeconds = 300;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public AlertRule getAlertRule() { return alertRule; }
    public void setAlertRule(AlertRule value) { alertRule = value; }
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel value) { channel = value; }
    public NotificationRecipient getRecipient() { return recipient; }
    public void setRecipient(NotificationRecipient value) { recipient = value; }
    public NotificationGroup getGroup() { return group; }
    public void setGroup(NotificationGroup value) { group = value; }
    public AlertRule.Severity getSeverity() { return severity; }
    public void setSeverity(AlertRule.Severity value) { severity = value; }
    public NotificationRoute.EventType getEventType() { return eventType; }
    public void setEventType(NotificationRoute.EventType value) { eventType = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public Integer getMinimumIntervalSeconds() { return minimumIntervalSeconds; }
    public void setMinimumIntervalSeconds(Integer value) { minimumIntervalSeconds = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
}
