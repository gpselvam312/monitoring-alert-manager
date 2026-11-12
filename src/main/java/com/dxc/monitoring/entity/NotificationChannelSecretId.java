package com.dxc.monitoring.entity;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NotificationChannelSecretId implements Serializable {
    @Column(name="channel_id") private Long channelId;
    @Column(name="secret_key", length=100) private String secretKey;
    public NotificationChannelSecretId() {}
    public NotificationChannelSecretId(Long channelId,String secretKey){this.channelId=channelId;this.secretKey=secretKey;}
    public Long getChannelId(){return channelId;} public void setChannelId(Long v){channelId=v;}
    public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;}
    @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NotificationChannelSecretId that))return false;return Objects.equals(channelId,that.channelId)&&Objects.equals(secretKey,that.secretKey);}
    @Override public int hashCode(){return Objects.hash(channelId,secretKey);}
}
