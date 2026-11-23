package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;

class UserServiceTest {

    @Test
    void createsApplicationRoleAssignmentAsTheSingleApplicationAccessRecord() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        UserApplicationRoleRepository assignments = mock(UserApplicationRoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService(users, roles, applications, assignments, encoder);

        User user = new User();
        user.setId(1L);
        Role platformAdminRole = role(1L, "ADMIN");
        Role applicationRole = role(2L, "OPERATOR");
        Application application = application(10L, true);
        when(roles.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(platformAdminRole));
        when(users.save(user)).thenReturn(user);
        when(encoder.encode("secret")).thenReturn("encoded");
        when(applications.findAllById(any())).thenReturn(List.of(application));
        when(roles.findAllById(any())).thenReturn(List.of(applicationRole));

        service.create(user, "secret", Map.of(10L, 2L), 10L, false);

        verify(assignments).deleteAllAssignmentsForUser(1L);
        org.mockito.ArgumentCaptor<UserApplicationRole> captor =
                org.mockito.ArgumentCaptor.forClass(UserApplicationRole.class);
        verify(assignments).save(captor.capture());
        assertEquals(10L, captor.getValue().getApplication().getId());
        assertEquals(2L, captor.getValue().getRole().getId());
        assertEquals(true, captor.getValue().isPrimary());
        assertEquals("encoded", user.getPasswordHash());
    }

    @Test
    void validatesApplicationAssignmentsBeforeReplacingExistingAssignments() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        UserApplicationRoleRepository assignments = mock(UserApplicationRoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService(users, roles, applications, assignments, encoder);

        User user = new User();
        user.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(roles.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(role(1L, "ADMIN")));
        when(applications.findAllById(any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.update(1L, "User", "user@example.com", true, null,
                        Map.of(99L, 2L), 99L, false));

        verify(assignments, never()).deleteAllAssignmentsForUser(1L);
    }

    private Application application(Long id, boolean enabled) {
        Application application = new Application();
        application.setId(id);
        application.setEnabled(enabled);
        return application;
    }

    private Role role(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }
}
