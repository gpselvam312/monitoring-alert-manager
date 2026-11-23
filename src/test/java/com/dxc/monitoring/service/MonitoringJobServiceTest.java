package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

class MonitoringJobServiceTest {

    @Test
    void savesJobOnlyWhenMachineBelongsToSelectedEnvironment() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        MonitoringSchedulerService scheduler = mock(MonitoringSchedulerService.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        MonitoringJobService service = new MonitoringJobService(jobs, scheduler, access);

        Application application = application(10L);
        Environment selectedEnvironment = environment(20L, application);
        Machine machine = new Machine();
        machine.setId(30L);
        machine.setEnvironment(environment(21L, application));

        MonitoringJob job = new MonitoringJob();
        job.setApplication(application);
        job.setEnvironment(selectedEnvironment);
        job.setMachine(machine);

        assertThrows(IllegalArgumentException.class, () -> service.save(job));
        verify(jobs, never()).save(job);
        verify(scheduler, never()).refreshJob(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void savesJobAfterCheckingApplicationAndEnvironmentConfigurationPermission() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        MonitoringSchedulerService scheduler = mock(MonitoringSchedulerService.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        MonitoringJobService service = new MonitoringJobService(jobs, scheduler, access);

        Application application = application(10L);
        Environment environment = environment(20L, application);
        MonitoringJob job = new MonitoringJob();
        job.setApplication(application);
        job.setEnvironment(environment);
        when(jobs.save(job)).thenReturn(job);

        assertEquals(job, service.save(job));
        verify(access).assertCanAccessApplication(10L, "MONITORING_CONFIG");
        verify(access).assertCanAccessEnvironment(10L, 20L, "MONITORING_CONFIG");
        verify(scheduler).refreshJob(null);
    }

    @Test
    void rejectsJobWhenEnvironmentPermissionIsDenied() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        MonitoringSchedulerService scheduler = mock(MonitoringSchedulerService.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        MonitoringJobService service = new MonitoringJobService(jobs, scheduler, access);

        Application application = application(10L);
        Environment environment = environment(20L, application);
        MonitoringJob job = new MonitoringJob();
        job.setApplication(application);
        job.setEnvironment(environment);
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(access).assertCanAccessEnvironment(10L, 20L, "MONITORING_CONFIG");

        assertThrows(AccessDeniedException.class, () -> service.save(job));
        verify(jobs, never()).save(job);
    }

    private Application application(Long id) {
        Application application = new Application();
        application.setId(id);
        return application;
    }

    private Environment environment(Long id, Application application) {
        Environment environment = new Environment();
        environment.setId(id);
        environment.setApplication(application);
        environment.setEnabled(true);
        return environment;
    }
}
