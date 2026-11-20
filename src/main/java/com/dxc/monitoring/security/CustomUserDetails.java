package com.dxc.monitoring.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CustomUserDetails extends User {
    private final Long userId;
    private final String fullName;
    private final Map<Long, Set<String>> applicationPermissions;
    private final Long primaryApplicationId;

    public CustomUserDetails(Long userId, String username, String password, boolean enabled,
            Collection<? extends GrantedAuthority> authorities, String fullName) {
        this(userId, username, password, enabled, authorities, fullName, Map.of(), null);
    }

    public CustomUserDetails(Long userId, String username, String password, boolean enabled,
            Collection<? extends GrantedAuthority> authorities, String fullName,
            Map<Long, Set<String>> applicationPermissions, Long primaryApplicationId) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.fullName = fullName;
        Map<Long, Set<String>> snapshot = new HashMap<>();
        if (applicationPermissions != null) {
            applicationPermissions.forEach((applicationId, permissions) ->
                    snapshot.put(applicationId, Set.copyOf(permissions)));
        }
        this.applicationPermissions = Map.copyOf(snapshot);
        this.primaryApplicationId = primaryApplicationId;
    }

    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public Long getPrimaryApplicationId() { return primaryApplicationId; }
    public boolean hasApplicationPermission(Long applicationId, String permission) {
        return applicationId != null && permission != null
                && applicationPermissions.getOrDefault(applicationId, Set.of()).contains(permission);
    }
    public Set<String> getApplicationPermissions(Long applicationId) {
        return applicationPermissions.getOrDefault(applicationId, Set.of());
    }
}
