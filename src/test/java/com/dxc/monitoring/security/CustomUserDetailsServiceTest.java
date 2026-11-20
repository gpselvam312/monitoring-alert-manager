package com.dxc.monitoring.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.entity.UserApplicationRoleId;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;

class CustomUserDetailsServiceTest {
    @Test
    void capturesApplicationPermissionsAndPrimaryApplicationAtLogin() {
        UserRepository users = mock(UserRepository.class);
        UserApplicationRoleRepository assignments = mock(UserApplicationRoleRepository.class);
        User user = new User();
        user.setId(9L);
        user.setUsername("operator");
        user.setPasswordHash("encoded");
        user.setEnabled(true);
        user.setFullName("Operator");
        when(users.findByUsername("operator")).thenReturn(java.util.Optional.of(user));

        Permission permission = new Permission();
        permission.setName("MONITORING_VIEW");
        Role role = new Role();
        role.setName("VIEWER");
        role.setPermissions(Set.of(permission));
        Application application = new Application();
        application.setId(42L);
        UserApplicationRole assignment = new UserApplicationRole();
        assignment.setId(new UserApplicationRoleId(9L, 42L));
        assignment.setUser(user);
        assignment.setApplication(application);
        assignment.setRole(role);
        assignment.setPrimary(true);
        when(assignments.findAssignmentsForUser(9L)).thenReturn(List.of(assignment));

        CustomUserDetails details = (CustomUserDetails) new CustomUserDetailsService(users, assignments)
                .loadUserByUsername("operator");

        assertTrue(details.hasApplicationPermission(42L, "MONITORING_VIEW"));
        assertFalse(details.hasApplicationPermission(43L, "MONITORING_VIEW"));
        assertEquals(42L, details.getPrimaryApplicationId());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "MONITORING_VIEW".equals(a.getAuthority())));
        assertFalse(details.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void grantsPlatformAdminAuthorityOnlyFromGlobalAdminRole() {
        UserRepository users = mock(UserRepository.class);
        UserApplicationRoleRepository assignments = mock(UserApplicationRoleRepository.class);
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("encoded");
        user.setEnabled(true);
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        user.setRoles(Set.of(adminRole));
        when(users.findByUsername("admin")).thenReturn(java.util.Optional.of(user));
        when(assignments.findAssignmentsForUser(1L)).thenReturn(List.of());

        CustomUserDetails details = (CustomUserDetails) new CustomUserDetailsService(users, assignments)
                .loadUserByUsername("admin");

        assertTrue(details.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}
