package com.dxc.monitoring.service.external;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.ExternalApplicationAccountRepository;
import com.dxc.monitoring.repository.ExternalApplicationRepository;
import com.dxc.monitoring.repository.ExternalApplicationTypeRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

class ExternalApplicationServiceTest {
    private ExternalApplicationRepository applications;
    private DashboardAccessService accessService;
    private ExternalApplicationService service;

    @BeforeEach
    void setUp() {
        applications = mock(ExternalApplicationRepository.class);
        accessService = mock(DashboardAccessService.class);
        service = new ExternalApplicationService(applications,
                mock(ExternalApplicationAccountRepository.class),
                mock(ExternalSecretEncryptionService.class),
                mock(ApplicationRepository.class), mock(EnvironmentRepository.class),
                mock(ExternalApplicationTypeRepository.class), accessService);
    }

    @Test
    void restrictsExternalApplicationSearchToApplicationsGrantedAtLogin() {
        Application app = new Application();
        app.setId(31L);
        when(accessService.getApplicationsWithPermission("MONITORING_CONFIG")).thenReturn(List.of(app));
        Pageable pageable = PageRequest.of(0, 5);
        when(applications.search("", List.of(31L), null, null, null, pageable)).thenReturn(Page.empty(pageable));

        assertTrue(service.findPage(" ", null, null, "", pageable).isEmpty());
        verify(applications).search("", List.of(31L), null, null, null, pageable);
    }

    @Test
    void loadsOnlyTheRequestedEnabledExternalApplicationWithinAuthorizedScope() {
        Application app = new Application();
        app.setId(31L);
        when(accessService.getApplicationsWithPermission("SYSTEM_CONFIG")).thenReturn(List.of(app));
        com.dxc.monitoring.entity.ExternalApplication external = new com.dxc.monitoring.entity.ExternalApplication();
        when(applications.findFirstByIdAndEnabledTrueAndApplication_IdIn(77L, List.of(31L)))
                .thenReturn(java.util.Optional.of(external));

        assertTrue(service.findEnabledByIdForCurrentUser(77L) == external);
        verify(applications).findFirstByIdAndEnabledTrueAndApplication_IdIn(77L, List.of(31L));
        verify(applications, never()).findEnabledForApplications(anyList());
    }

    @Test
    void deniesRequestedExternalApplicationOutsideAuthorizedScope() {
        Application app = new Application();
        app.setId(31L);
        when(accessService.getApplicationsWithPermission("SYSTEM_CONFIG")).thenReturn(List.of(app));
        when(applications.findFirstByIdAndEnabledTrueAndApplication_IdIn(77L, List.of(31L)))
                .thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.findEnabledByIdForCurrentUser(77L));
    }

    @Test
    void doesNotQueryExternalApplicationsWhenUserHasNoConfigurationScope() {
        when(accessService.getApplicationsWithPermission("MONITORING_CONFIG")).thenReturn(List.of());
        Pageable pageable = PageRequest.of(0, 5);

        assertTrue(service.findPage("", null, null, null, pageable).isEmpty());
        verifyNoInteractions(applications);
    }
}
