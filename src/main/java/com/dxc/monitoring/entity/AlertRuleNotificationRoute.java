package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.persistence.*;

@Entity
@Table(name = "alert_rule_notification_routes", schema = "ra_fcb")
public class AlertRuleNotificationRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "channel_id", nullable = false)
    private NotificationChannel channel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_id")
    private NotificationRecipient recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_id")
    private NotificationGroup group;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "alert_rule_notification_route_recipients", schema = "ra_fcb",
            joinColumns = @JoinColumn(name = "route_id"), inverseJoinColumns = @JoinColumn(name = "recipient_id"))
    private Set<NotificationRecipient> recipients = new LinkedHashSet<>();
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "alert_rule_notification_route_groups", schema = "ra_fcb",
            joinColumns = @JoinColumn(name = "route_id"), inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<NotificationGroup> groups = new LinkedHashSet<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AlertRule.Severity severity;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30)
    private NotificationRoute.EventType eventType;
    @Column(name = "cc_recipients", columnDefinition = "text")
    private String ccRecipients;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "minimum_interval_seconds", nullable = false)
    private Integer minimumIntervalSeconds = 300;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @PrePersist protected void onCreate() { OffsetDateTime now=OffsetDateTime.now(); if(createdAt==null)createdAt=now; if(updatedAt==null)updatedAt=now; if(minimumIntervalSeconds==null)minimumIntervalSeconds=300; }
    @PreUpdate protected void onUpdate() { updatedAt=OffsetDateTime.now(); }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public AlertRule getAlertRule(){return alertRule;} public void setAlertRule(AlertRule v){alertRule=v;}
    public NotificationChannel getChannel(){return channel;} public void setChannel(NotificationChannel v){channel=v;}
    public NotificationRecipient getRecipient(){return recipient;} public void setRecipient(NotificationRecipient v){recipient=v;}
    public NotificationGroup getGroup(){return group;} public void setGroup(NotificationGroup v){group=v;}
    public Set<NotificationRecipient> getRecipients(){return recipients;} public void setRecipients(Set<NotificationRecipient> v){recipients=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
    public Set<NotificationGroup> getGroups(){return groups;} public void setGroups(Set<NotificationGroup> v){groups=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
    public AlertRule.Severity getSeverity(){return severity;} public void setSeverity(AlertRule.Severity v){severity=v;}
    public NotificationRoute.EventType getEventType(){return eventType;} public void setEventType(NotificationRoute.EventType v){eventType=v;}
    public String getCcRecipients(){return ccRecipients;} public void setCcRecipients(String v){ccRecipients=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public Integer getMinimumIntervalSeconds(){return minimumIntervalSeconds;} public void setMinimumIntervalSeconds(Integer v){minimumIntervalSeconds=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}