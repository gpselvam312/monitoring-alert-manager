package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;


import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.AlertCleanupRunRepository;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;
import com.dxc.monitoring.repository.AlertTypeRepository;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

class AlertRetentionServiceTest {

    @Test
    void allowsApplicationAndEnvironmentScopeForEveryRetentionDataType() {
        AlertRetentionPolicyRepository policyRepository = mock(AlertRetentionPolicyRepository.class);
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        Application application = new Application();
        application.setId(11L);
        Environment environment = new Environment();
        environment.setId(22L);
        environment.setApplication(application);

        when(applicationRepository.findById(11L)).thenReturn(Optional.of(application));
        when(environmentRepository.findById(22L)).thenReturn(Optional.of(environment));
        when(policyRepository.existsForScope(any(), any(), any(), any())).thenReturn(false);
        when(policyRepository.save(any(AlertRetentionPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlertRetentionService service = new AlertRetentionService(
                policyRepository,
                mock(AlertCleanupRunRepository.class),
                mock(AlertRetentionCleanupExecutor.class),
                mock(AlertCleanupAuditService.class),
                applicationRepository,
                environmentRepository,
                mock(DashboardAccessService.class),
                mock(AlertTypeRepository.class), mock(JdbcTemplate.class));

        for (AlertRetentionPolicy.DataType dataType : AlertRetentionPolicy.DataType.values()) {
            AlertRetentionPolicy saved = service.createPolicy(
                    dataType.name(), 90, false, null, 11L, 22L, "ALL", 7L);
            assertEquals(dataType, saved.getDataType());
            assertEquals(11L, saved.getApplication().getId());
            assertEquals(22L, saved.getEnvironment().getId());
        }
    }
    @Test
    void deniesGlobalRetentionPolicyToNonPlatformAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "n/a",
                        java.util.List.of(new SimpleGrantedAuthority("DATA_EXPUNGE"))));
        try {
            AlertRetentionService service = createService();
            assertThrows(AccessDeniedException.class,
                    () -> service.createPolicy("RESOLVED_ALERTS", 90, false, null, null, null, "ALL", 7L));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void allowsGlobalRetentionPolicyForPlatformAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            AlertRetentionService service = createService();
            AlertRetentionPolicy saved = service.createPolicy(
                    "RESOLVED_ALERTS", 90, false, null, null, null, "ALL", 1L);
            assertEquals(null, saved.getApplication());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }


    @Test
    void scopesCleanupAuditHistoryToApplicationsTheUserCanExpunge() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "n/a",
                        List.of(new SimpleGrantedAuthority("DATA_EXPUNGE"))));
        try {
            AlertCleanupRunRepository runs = mock(AlertCleanupRunRepository.class);
            DashboardAccessService access = mock(DashboardAccessService.class);
            Application application = new Application();
            application.setId(31L);
            when(access.getApplicationsWithPermission("DATA_EXPUNGE")).thenReturn(List.of(application));
            Pageable pageable = PageRequest.of(0, 20);
            when(runs.findVisibleRuns(false, List.of(31L), pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));
            AlertRetentionService service = new AlertRetentionService(
                    mock(AlertRetentionPolicyRepository.class), runs,
                    mock(AlertRetentionCleanupExecutor.class), mock(AlertCleanupAuditService.class),
                    mock(ApplicationRepository.class), mock(EnvironmentRepository.class),
                    access, mock(AlertTypeRepository.class), mock(JdbcTemplate.class));

            service.findVisibleCleanupRuns(pageable);

            verify(runs).findVisibleRuns(false, List.of(31L), pageable);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void platformAdministratorCanSeeGlobalCleanupAuditHistory() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            AlertCleanupRunRepository runs = mock(AlertCleanupRunRepository.class);
            DashboardAccessService access = mock(DashboardAccessService.class);
            Pageable pageable = PageRequest.of(0, 20);
            when(access.getApplicationsWithPermission("DATA_EXPUNGE")).thenReturn(List.of());
            when(runs.findVisibleRuns(true, List.of(), pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));
            AlertRetentionService service = new AlertRetentionService(
                    mock(AlertRetentionPolicyRepository.class), runs,
                    mock(AlertRetentionCleanupExecutor.class), mock(AlertCleanupAuditService.class),
                    mock(ApplicationRepository.class), mock(EnvironmentRepository.class),
                    access, mock(AlertTypeRepository.class), mock(JdbcTemplate.class));

            service.findVisibleCleanupRuns(pageable);

            verify(runs).findVisibleRuns(true, List.of(), pageable);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AlertRetentionService createService() {
        AlertRetentionPolicyRepository policies = mock(AlertRetentionPolicyRepository.class);
        when(policies.existsForScope(any(), any(), any(), any())).thenReturn(false);
        when(policies.save(any(AlertRetentionPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new AlertRetentionService(policies, mock(AlertCleanupRunRepository.class),
                mock(AlertRetentionCleanupExecutor.class), mock(AlertCleanupAuditService.class),
                mock(ApplicationRepository.class), mock(EnvironmentRepository.class),
                mock(DashboardAccessService.class), mock(AlertTypeRepository.class), mock(JdbcTemplate.class));
    }

}
