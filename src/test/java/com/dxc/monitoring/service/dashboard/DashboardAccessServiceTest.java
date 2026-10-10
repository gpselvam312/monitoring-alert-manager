package com.dxc.monitoring.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.entity.UserApplicationRoleId;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DashboardAccessServiceTest {
    @Mock private ApplicationRepository applications;
    @Mock private EnvironmentRepository environments;
    @Mock private UserRepository users;
    @Mock private UserApplicationRoleRepository assignments;

    @InjectMocks private DashboardAccessService service;

    private User user;
    private Application appA;
    private Application appB;
    private Role viewerRole;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setUsername("viewer1");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("viewer1", "password", List.of()));
        when(users.findByUsername("viewer1")).thenReturn(Optional.of(user));

        appA = new Application();
        appA.setId(11L);
        appA.setName("App A");
        appA.setEnabled(true);

        appB = new Application();
        appB.setId(12L);
        appB.setName("App B");
        appB.setEnabled(true);

        Permission view = new Permission();
        view.setName("MONITORING_VIEW");
        viewerRole = new Role();
        viewerRole.setName("VIEWER");
        viewerRole.setPermissions(Set.of(view));

        UserApplicationRole assignment = new UserApplicationRole();
        assignment.setId(new UserApplicationRoleId(7L, 11L));
        assignment.setUser(user);
        assignment.setApplication(appA);
        assignment.setRole(viewerRole);
        when(assignments.findByUser_IdAndApplication_Id(7L, 11L)).thenReturn(Optional.of(assignment));
        when(applications.findById(11L)).thenReturn(Optional.of(appA));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyEnvironmentsOwnedByTheSelectedApplication() {
        Environment sit = new Environment();
        sit.setId(101L);
        sit.setName("SIT");
        sit.setApplication(appA);
        sit.setEnabled(true);
        when(environments.findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(11L))
                .thenReturn(List.of(sit));

        List<Environment> result = service.getEnvironmentsForApplication(11L);

        assertEquals(List.of(sit), result);
    }

    @Test
    void rejectsAnEnvironmentOwnedByAnotherApplication() {
        Environment appBProd = new Environment();
        appBProd.setId(202L);
        appBProd.setName("PROD");
        appBProd.setApplication(appB);
        appBProd.setEnabled(true);
        when(environments.findById(202L)).thenReturn(Optional.of(appBProd));

        assertThrows(AccessDeniedException.class,
                () -> service.assertCanAccessEnvironment(11L, 202L));
    }

    @Test
    void rejectsActionsNotGrantedByTheSelectedApplicationRole() {
        assertThrows(AccessDeniedException.class,
                () -> service.assertCanAccessApplication(11L, "MONITORING_CONFIG"));
    }
}
