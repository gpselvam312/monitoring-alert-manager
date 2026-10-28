package com.dxc.monitoring.entity;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NotificationGroupMemberId implements Serializable {
    @Column(name="group_id") private Long groupId;
    @Column(name="recipient_id") private Long recipientId;
    public NotificationGroupMemberId() {}
    public NotificationGroupMemberId(Long groupId, Long recipientId){this.groupId=groupId;this.recipientId=recipientId;}
    public Long getGroupId(){return groupId;} public void setGroupId(Long v){groupId=v;}
    public Long getRecipientId(){return recipientId;} public void setRecipientId(Long v){recipientId=v;}
    @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NotificationGroupMemberId that))return false;return Objects.equals(groupId,that.groupId)&&Objects.equals(recipientId,that.recipientId);}
    @Override public int hashCode(){return Objects.hash(groupId,recipientId);}
}
