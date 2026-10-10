package com.dxc.monitoring.security;

import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
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
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final UserApplicationRoleRepository userApplicationRoleRepository;

    public CustomUserDetailsService(UserRepository userRepository,
            UserApplicationRoleRepository userApplicationRoleRepository) {
        this.userRepository = userRepository;
        this.userApplicationRoleRepository = userApplicationRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        // The legacy global ADMIN role remains the platform-wide administrator role.
        Set<Role> platformAdminRoles = user.getRoles().stream()
                .filter(role -> "ADMIN".equals(role.getName())).collect(Collectors.toSet());
        if (!platformAdminRoles.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            platformAdminRoles.stream().flatMap(role -> role.getPermissions().stream())
                    .map(Permission::getName).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        }

        // Application roles contribute menu/action permissions, but never global ROLE_ADMIN.
        // Data services must still verify the role against the specific application being accessed.
        Set<UserApplicationRole> assignments = new HashSet<>(
                userApplicationRoleRepository.findAssignmentsForUser(user.getId()));
        assignments.stream().map(UserApplicationRole::getRole).filter(role -> role != null)
                .flatMap(role -> role.getPermissions().stream()).map(Permission::getName)
                .map(SimpleGrantedAuthority::new).forEach(authorities::add);

        return new CustomUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.isEnabled(), authorities, user.getFullName());
    }
}
