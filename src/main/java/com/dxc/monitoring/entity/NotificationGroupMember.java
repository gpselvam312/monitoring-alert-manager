package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_group_members", schema = "ra_fcb")
public class NotificationGroupMember {
    @EmbeddedId private NotificationGroupMemberId id = new NotificationGroupMemberId();
    @MapsId("groupId") @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id", nullable = false)
    private NotificationGroup group;
    @MapsId("recipientId") @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id", nullable = false)
    private NotificationRecipient recipient;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @PrePersist protected void onCreate(){if(createdAt==null)createdAt=OffsetDateTime.now();}
    public NotificationGroupMemberId getId(){return id;} public void setId(NotificationGroupMemberId v){id=v;}
    public NotificationGroup getGroup(){return group;} public void setGroup(NotificationGroup v){group=v;if(v!=null){if(id==null)id=new NotificationGroupMemberId();id.setGroupId(v.getId());}}
    public NotificationRecipient getRecipient(){return recipient;} public void setRecipient(NotificationRecipient v){recipient=v;if(v!=null){if(id==null)id=new NotificationGroupMemberId();id.setRecipientId(v.getId());}}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
}
