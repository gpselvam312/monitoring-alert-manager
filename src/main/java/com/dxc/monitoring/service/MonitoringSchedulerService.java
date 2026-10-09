package com.dxc.monitoring.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.Schedule;
import com.dxc.monitoring.repository.MonitoringJobRepository;

@Service
public class MonitoringSchedulerService
{
    private final MonitoringJobRepository monitoringJobRepository;
    private final MonitoringExecutionManager monitoringExecutionManager;
    private final ThreadPoolTaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    public MonitoringSchedulerService(MonitoringJobRepository monitoringJobRepository,
            MonitoringExecutionManager monitoringExecutionManager,
            ThreadPoolTaskScheduler taskScheduler)
    {
        this.monitoringJobRepository = monitoringJobRepository;
        this.monitoringExecutionManager = monitoringExecutionManager;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void initialize()
    {
        monitoringJobRepository.findAll().stream()
                .filter(this::isSchedulable)
                .forEach(this::scheduleJob);
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

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeIfWithinWindow(job.getId()),
                trigger);

        if (future != null)
        {
            scheduledJobs.put(job.getId(), future);
        }
    }

    private Trigger createCronTrigger(Schedule schedule)
    {
        if (schedule.getCronExpression() == null || schedule.getCronExpression().isBlank())
        {
            return null;
        }

        ZoneId zoneId = ZoneId.of(schedule.getTimezone());
        return new CronTrigger(schedule.getCronExpression().trim(), zoneId);
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

    private void executeIfWithinWindow(Long jobId)
    {
        MonitoringJob job = monitoringJobRepository.findByIdForDetails(jobId).orElse(null);

        if (!isSchedulable(job) || !isWithinWindow(job.getSchedule()))
        {
            return;
        }

        try
        {
            monitoringExecutionManager.execute(job);
        }
        catch (Exception exception)
        {
            // The execution manager records execution failures. Scheduling must continue
            // even when a single scheduled execution cannot be started.
            System.err.println("Scheduled monitoring job " + jobId + " was not executed: "
                    + exception.getMessage());
        }
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

        ZoneId zoneId = ZoneId.of(schedule.getTimezone());
        LocalTime now = ZonedDateTime.now(zoneId).toLocalTime();

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
