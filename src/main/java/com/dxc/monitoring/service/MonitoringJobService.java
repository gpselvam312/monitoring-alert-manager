package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;

@Service
public class MonitoringJobService
{

    private final MonitoringJobRepository monitoringJobRepository;
    private final MonitoringSchedulerService monitoringSchedulerService;

    public MonitoringJobService(MonitoringJobRepository monitoringJobRepository,
            MonitoringSchedulerService monitoringSchedulerService)
    {
        this.monitoringJobRepository = monitoringJobRepository;
        this.monitoringSchedulerService = monitoringSchedulerService;
    }

    @Transactional(readOnly = true)
    public Page<MonitoringJob> findAll(String search, Pageable pageable)
    {
        return monitoringJobRepository.findAllForList(search, MonitoringJob.ExecutionMode.STANDARD, pageable);
    }

    @Transactional(readOnly = true)
    public MonitoringJob findById(Long id)
    {
        return monitoringJobRepository.findByIdForDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + id));
    }

    @Transactional
    public MonitoringJob save(MonitoringJob job)
    {
        MonitoringJob saved = monitoringJobRepository.save(job);
        monitoringSchedulerService.refreshJob(saved.getId());
        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String delete(Long id)
    {
        MonitoringJob job = monitoringJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + id));

        String jobName = job.getName();

        monitoringJobRepository.delete(job);
        monitoringSchedulerService.cancelJob(id);

        return jobName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        MonitoringJob job = findById(id);
        job.setEnabled(!job.isEnabled());
        monitoringJobRepository.save(job);
        monitoringSchedulerService.refreshJob(job.getId());
    }
}
