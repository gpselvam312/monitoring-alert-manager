package com.dxc.monitoring.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.ReferenceDataCacheService;

@ExtendWith(MockitoExtension.class)
class DashboardAccessServiceTest {
    @Mock private ReferenceDataCacheService referenceDataCache;
    @InjectMocks private DashboardAccessService service;
    private Application appA;
    private Application appB;

    @BeforeEach
    void setUp() {
        appA = application(11L, "App A");
        appB = application(12L, "App B");
        CustomUserDetails details = new CustomUserDetails(7L, "viewer1", "password", true,
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")), "Viewer One",
                Map.of(11L, Set.of("MONITORING_VIEW")), 12L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, "password", details.getAuthorities()));
        when(referenceDataCache.getEnabledApplications()).thenReturn(List.of(appA, appB));
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void returnsConfiguredPrimaryApplicationIdFromLoginSnapshot() {
        assertEquals(12L, service.getPrimaryApplicationId());
    }

    @Test void returnsOnlyApplicationsAndEnvironmentsGrantedAtLogin() {
        Environment sit = new Environment();
        sit.setId(101L); sit.setName("SIT"); sit.setApplication(appA); sit.setEnabled(true);
        when(referenceDataCache.getEnabledEnvironments(11L)).thenReturn(List.of(sit));
        assertEquals(List.of(appA), service.getApplicationsWithPermission("MONITORING_VIEW"));
        assertEquals(List.of(sit), service.getEnvironmentsForApplication(11L));
    }

    @Test void rejectsAnEnvironmentOwnedByAnotherApplication() {
        Environment appBProd = new Environment();
        appBProd.setId(202L); appBProd.setName("PROD"); appBProd.setApplication(appB); appBProd.setEnabled(true);
        when(referenceDataCache.getEnabledEnvironments(11L)).thenReturn(List.of(appBProd));
        assertThrows(AccessDeniedException.class, () -> service.assertCanAccessEnvironment(11L, 202L));
    }

    @Test void rejectsActionsNotGrantedByTheSelectedApplicationRole() {
        assertThrows(AccessDeniedException.class,
                () -> service.assertCanAccessApplication(11L, "MONITORING_CONFIG"));
    }

    @Test void platformAdministratorCanAccessEveryEnabledApplication() {
        CustomUserDetails admin = new CustomUserDetails(1L, "admin", "password", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "Administrator");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(admin, "password", admin.getAuthorities()));
        assertEquals(List.of(appA, appB), service.getApplicationsWithPermission("MACHINE_CONFIG"));
    }

    private Application application(Long id, String name) {
        Application application = new Application();
        application.setId(id); application.setName(name); application.setEnabled(true);
        return application;
    }
}
