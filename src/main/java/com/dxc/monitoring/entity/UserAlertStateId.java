package com.dxc.monitoring.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserAlertStateId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "alert_id")
    private Long alertId;

    public UserAlertStateId() {}
    public UserAlertStateId(Long userId, Long alertId) {
        this.userId = userId;
        this.alertId = alertId;
    }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserAlertStateId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, alertId); }
}
