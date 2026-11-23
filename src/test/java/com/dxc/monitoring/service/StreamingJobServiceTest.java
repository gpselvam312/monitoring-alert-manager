package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.UserRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.service.executor.ScriptSecurityValidator;

class StreamingJobServiceTest {

    @Test
    void pagedStreamingListRequiresMonitoringViewApplicationScope() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        when(access.getApplicationsWithPermission("MONITORING_VIEW")).thenReturn(List.of());
        StreamingJobService service = new StreamingJobService(jobs, mock(UserRepository.class),
                mock(JdbcTemplate.class), access, mock(ScriptSecurityValidator.class));

        try {
            assertTrue(service.listJobs("", PageRequest.of(0, 10)).isEmpty());
            verify(access).getApplicationsWithPermission("MONITORING_VIEW");
            verifyNoInteractions(jobs);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void unpagedStreamingListRequiresMonitoringViewApplicationScope() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        when(access.getApplicationsWithPermission("MONITORING_VIEW")).thenReturn(List.of());
        StreamingJobService service = new StreamingJobService(jobs, mock(UserRepository.class),
                mock(JdbcTemplate.class), access, mock(ScriptSecurityValidator.class));

        try {
            assertTrue(service.listJobs().isEmpty());
            verify(access).getApplicationsWithPermission("MONITORING_VIEW");
            verifyNoInteractions(jobs);
        } finally {
            service.shutdown();
        }
    }
}
