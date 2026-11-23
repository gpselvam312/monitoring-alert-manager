package com.dxc.monitoring.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dxc.monitoring.config.BackgroundWorkReadiness;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.Schedule;
import com.dxc.monitoring.repository.MonitoringJobRepository;

class MonitoringSchedulerServiceTest {

    @Test
    void reschedulesChangedJobConfigurationAcrossInstances() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        MonitoringExecutionManager executions = mock(MonitoringExecutionManager.class);
        ThreadPoolTaskScheduler scheduler = mock(ThreadPoolTaskScheduler.class);
        BackgroundWorkReadiness readiness = mock(BackgroundWorkReadiness.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(readiness.isReady()).thenReturn(true);
        when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(future);

        MonitoringJob job = new MonitoringJob();
        job.setId(10L);
        job.setEnabled(true);
        job.setExecutionMode(MonitoringJob.ExecutionMode.STANDARD);
        Schedule schedule = new Schedule();
        schedule.setId(20L);
        schedule.setEnabled(true);
        schedule.setType(Schedule.ScheduleType.CRON);
        schedule.setCronExpression("0 * * * * *");
        job.setSchedule(schedule);
        when(jobs.findSchedulableJobs(MonitoringJob.ExecutionMode.STANDARD))
                .thenReturn(List.of(job));

        MonitoringSchedulerService service = new MonitoringSchedulerService(jobs, executions, scheduler, readiness);
        service.reconcileSchedules();
        schedule.setCronExpression("30 * * * * *");
        service.reconcileSchedules();

        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        verify(future).cancel(false);
    }

    @Test
    void doesNotReconcileBeforeStartupReadiness() {
        MonitoringJobRepository jobs = mock(MonitoringJobRepository.class);
        BackgroundWorkReadiness readiness = mock(BackgroundWorkReadiness.class);
        when(readiness.isReady()).thenReturn(false);

        MonitoringSchedulerService service = new MonitoringSchedulerService(jobs,
                mock(MonitoringExecutionManager.class), mock(ThreadPoolTaskScheduler.class), readiness);
        service.reconcileSchedules();

        verifyNoInteractions(jobs);
    }
}
