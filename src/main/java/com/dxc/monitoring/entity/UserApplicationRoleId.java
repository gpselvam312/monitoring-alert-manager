package com.dxc.monitoring.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserApplicationRoleId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "application_id")
    private Long applicationId;

    public UserApplicationRoleId() {}

    public UserApplicationRoleId(Long userId, Long applicationId) {
        this.userId = userId;
        this.applicationId = applicationId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserApplicationRoleId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(applicationId, that.applicationId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, applicationId); }
}
