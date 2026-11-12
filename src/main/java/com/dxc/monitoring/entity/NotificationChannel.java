package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_channels", schema = "ra_fcb")
public class NotificationChannel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING) @Column(name = "channel_type", nullable = false, length = 30)
    private ChannelType channelType;
    @Column(nullable = false)
    private boolean enabled = true;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private String configuration = "{}";
    @Column(name = "credential_reference", length = 255)
    private String credentialReference;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "updated_by")
    private Long updatedBy;
    @PrePersist protected void onCreate() { OffsetDateTime n=OffsetDateTime.now(); if(createdAt==null)createdAt=n; if(updatedAt==null)updatedAt=n; if(configuration==null)configuration="{}"; }
    @PreUpdate protected void onUpdate() { updatedAt=OffsetDateTime.now(); }
    public enum ChannelType { EMAIL, TEAMS, WHATSAPP }
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public ChannelType getChannelType(){return channelType;} public void setChannelType(ChannelType v){channelType=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getConfiguration(){return configuration;} public void setConfiguration(String v){configuration=v;}
    public String getCredentialReference(){return credentialReference;} public void setCredentialReference(String v){credentialReference=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;}
}
