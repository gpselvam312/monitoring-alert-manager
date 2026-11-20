package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

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
                mock(AlertTypeRepository.class));

        for (AlertRetentionPolicy.DataType dataType : AlertRetentionPolicy.DataType.values()) {
            AlertRetentionPolicy saved = service.createPolicy(
                    dataType.name(), 90, false, null, 11L, 22L, "ALL", 7L);
            assertEquals(dataType, saved.getDataType());
            assertEquals(11L, saved.getApplication().getId());
            assertEquals(22L, saved.getEnvironment().getId());
        }
    }
}
