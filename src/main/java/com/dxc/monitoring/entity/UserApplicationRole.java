package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_application_roles", schema = "ra_fcb")
public class UserApplicationRole {
    @EmbeddedId
    private UserApplicationRoleId id = new UserApplicationRoleId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("applicationId")
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    public UserApplicationRoleId getId() { return id; }
    public void setId(UserApplicationRoleId id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; if (id == null) id = new UserApplicationRoleId(); if (user != null) id.setUserId(user.getId()); }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; if (id == null) id = new UserApplicationRoleId(); if (application != null) id.setApplicationId(application.getId()); }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public OffsetDateTime getCreatedAt() { return null; }
}
