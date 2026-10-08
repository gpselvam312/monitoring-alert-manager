package com.dxc.monitoring.security;

import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService
{
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
    {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        /*
         * Add role authorities.
         *
         * Example: ADMIN -> ROLE_ADMIN OPERATOR -> ROLE_OPERATOR VIEWER -> ROLE_VIEWER
         */
        Set<SimpleGrantedAuthority> roleAuthorities = user.getRoles().stream().map(Role::getName)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toSet());

        authorities.addAll(roleAuthorities);

        /*
         * Add permission authorities.
         *
         * Example: MONITORING_VIEW MONITORING_RUN MONITORING_CONFIG
         */
        Set<SimpleGrantedAuthority> permissionAuthorities =
            user.getRoles().stream().flatMap(role -> role.getPermissions().stream()).map(Permission::getName)
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());

        authorities.addAll(permissionAuthorities);

        return new CustomUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(),
                authorities, user.getFullName());
    }
}
