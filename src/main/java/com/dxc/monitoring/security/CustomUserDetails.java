package com.dxc.monitoring.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CustomUserDetails extends User
{
    private final Long userId;
    private final String fullName;

    public CustomUserDetails(Long userId, String username, String password, boolean enabled,
            Collection<? extends GrantedAuthority> authorities, String fullName)
    {
        super(username, password, enabled, true, true, true, authorities);

        this.userId = userId;
        this.fullName = fullName;
    }

    public Long getUserId()
    {
        return userId;
    }

    public String getFullName()
    {
        return fullName;
    }
}
