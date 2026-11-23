package com.dxc.monitoring.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dxc.monitoring.config.BackgroundWorkReadiness;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.Schedule;
import com.dxc.monitoring.repository.MonitoringJobRepository;

@Service
public class MonitoringSchedulerService
{
    private static final Logger log = LoggerFactory.getLogger(MonitoringSchedulerService.class);
    private final MonitoringJobRepository monitoringJobRepository;
    private final MonitoringExecutionManager monitoringExecutionManager;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final BackgroundWorkReadiness readiness;
    private final ExecutorService executionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();
    private final Map<Long, ScheduleFingerprint> scheduleFingerprints = new ConcurrentHashMap<>();

    public MonitoringSchedulerService(MonitoringJobRepository monitoringJobRepository,
            MonitoringExecutionManager monitoringExecutionManager,
            ThreadPoolTaskScheduler taskScheduler, BackgroundWorkReadiness readiness)
    {
        this.monitoringJobRepository = monitoringJobRepository;
        this.monitoringExecutionManager = monitoringExecutionManager;
        this.taskScheduler = taskScheduler;
        this.readiness = readiness;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void initialize()
    {
        monitoringJobRepository.findSchedulableJobs(MonitoringJob.ExecutionMode.STANDARD)
                .forEach(this::scheduleJob);
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${monitoring.scheduler.reconcile-interval-ms:30000}")
    @Transactional(readOnly = true)
    public synchronized void reconcileSchedules() {
        if (!readiness.isReady()) return;
        var currentJobs = monitoringJobRepository.findSchedulableJobs(MonitoringJob.ExecutionMode.STANDARD);
        Set<Long> currentIds = new HashSet<>();
        for (MonitoringJob job : currentJobs) {
            currentIds.add(job.getId());
            ScheduleFingerprint fingerprint = scheduleFingerprint(job);
            if (!Objects.equals(scheduleFingerprints.get(job.getId()), fingerprint)) {
                cancelJob(job.getId());
                scheduleJob(job);
            }
        }
        for (Long scheduledId : new HashSet<>(scheduledJobs.keySet())) {
            if (!currentIds.contains(scheduledId)) cancelJob(scheduledId);
        }
    }

    public synchronized void refreshJob(Long jobId)
    {
        cancelJob(jobId);

        monitoringJobRepository.findByIdForDetails(jobId)
                .filter(this::isSchedulable)
                .ifPresent(this::scheduleJob);
    }

    public synchronized void refreshSchedule(Long scheduleId)
    {
        monitoringJobRepository.findByScheduleId(scheduleId).forEach(job -> refreshJob(job.getId()));
    }

    public synchronized void cancelSchedule(Long scheduleId)
    {
        monitoringJobRepository.findByScheduleId(scheduleId).forEach(job -> cancelJob(job.getId()));
    }

    public synchronized void cancelJob(Long jobId)
    {
        ScheduledFuture<?> future = scheduledJobs.remove(jobId);
        scheduleFingerprints.remove(jobId);
        if (future != null)
        {
            future.cancel(false);
        }
    }

    private void scheduleJob(MonitoringJob job)
    {
        Schedule schedule = job.getSchedule();

        Trigger trigger = switch (schedule.getType())
        {
            case CRON -> createCronTrigger(schedule);
            case FIXED_DELAY -> createPeriodicTrigger(schedule, false);
            case FIXED_RATE -> createPeriodicTrigger(schedule, true);
        };

        if (trigger == null)
        {
            return;
        }

        ScheduleFingerprint expectedFingerprint = scheduleFingerprint(job);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> dispatchScheduledExecution(job.getId(), expectedFingerprint),
                trigger);

        if (future != null)
        {
            scheduledJobs.put(job.getId(), future);
            scheduleFingerprints.put(job.getId(), scheduleFingerprint(job));
        }
    }

    private void dispatchScheduledExecution(Long jobId, ScheduleFingerprint expectedFingerprint) {
        try {
            executionExecutor.execute(() -> {
                try {
                    MonitoringJob job = monitoringJobRepository.findByIdForDetails(jobId).orElse(null);
                    if (!isSchedulable(job) || !scheduleFingerprint(job).equals(expectedFingerprint)
                            || !isWithinWindow(job.getSchedule())) return;
                    monitoringExecutionManager.execute(job, MonitoringExecution.TriggerSource.SCHEDULED);
                } catch (RuntimeException exception) {
                    log.error("Scheduled execution failed for monitoring job {}", jobId, exception);
                }
            });
        } catch (RuntimeException exception) {
            log.error("Could not dispatch scheduled execution for monitoring job {}", jobId, exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executionExecutor.shutdownNow();
    }

    private ScheduleFingerprint scheduleFingerprint(MonitoringJob job) {
        Schedule schedule = job.getSchedule();
        return new ScheduleFingerprint(job.getUpdatedAt(), job.isEnabled(), job.getExecutionMode(),
                schedule.getId(), schedule.getUpdatedAt(), schedule.isEnabled(), schedule.getType(),
                schedule.getCronExpression(), schedule.getFixedDelaySeconds(), schedule.getFixedRateSeconds(),
                schedule.getStartTime(), schedule.getEndTime());
    }

    private record ScheduleFingerprint(LocalDateTime jobUpdatedAt, boolean jobEnabled,
            MonitoringJob.ExecutionMode executionMode, Long scheduleId, LocalDateTime scheduleUpdatedAt,
            boolean scheduleEnabled, Schedule.ScheduleType scheduleType, String cronExpression,
            Integer fixedDelaySeconds, Integer fixedRateSeconds, LocalTime startTime, LocalTime endTime) { }


    private Trigger createCronTrigger(Schedule schedule)
    {
        if (schedule.getCronExpression() == null || schedule.getCronExpression().isBlank())
        {
            return null;
        }

        return new CronTrigger(schedule.getCronExpression().trim());
    }

    private Trigger createPeriodicTrigger(Schedule schedule, boolean fixedRate)
    {
        Integer seconds = fixedRate ? schedule.getFixedRateSeconds() : schedule.getFixedDelaySeconds();

        if (seconds == null || seconds <= 0)
        {
            return null;
        }

        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(seconds));
        trigger.setFixedRate(fixedRate);
        return trigger;
    }

    private boolean isSchedulable(MonitoringJob job)
    {
        return job != null
                && job.isEnabled()
                && job.getExecutionMode() == MonitoringJob.ExecutionMode.STANDARD
                && job.getSchedule() != null
                && job.getSchedule().isEnabled()
                && job.getSchedule().getType() != null;
    }

    private boolean isWithinWindow(Schedule schedule)
    {
        LocalTime start = schedule.getStartTime();
        LocalTime end = schedule.getEndTime();

        if (start == null && end == null)
        {
            return true;
        }

        LocalTime now = LocalTime.now();

        if (start != null && end != null)
        {
            if (!start.isAfter(end))
            {
                return !now.isBefore(start) && !now.isAfter(end);
            }

            return !now.isBefore(start) || !now.isAfter(end);
        }

        if (start != null)
        {
            return !now.isBefore(start);
        }

        return !now.isAfter(end);
    }
}
