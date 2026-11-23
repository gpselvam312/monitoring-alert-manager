package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Service
public class MonitoringJobService
{

    private final MonitoringJobRepository monitoringJobRepository;
    private final MonitoringSchedulerService monitoringSchedulerService;
    private final DashboardAccessService accessService;

    public MonitoringJobService(MonitoringJobRepository monitoringJobRepository,
            MonitoringSchedulerService monitoringSchedulerService, DashboardAccessService accessService)
    {
        this.monitoringJobRepository = monitoringJobRepository;
        this.monitoringSchedulerService = monitoringSchedulerService;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Page<MonitoringJob> findAll(String search, Pageable pageable)
    {
        java.util.List<Long> applicationIds = accessService.getAccessibleApplications().stream()
                .map(com.dxc.monitoring.entity.Application::getId).toList();
        if (applicationIds.isEmpty()) return Page.empty(pageable);
        return monitoringJobRepository.findAllForApplications(search, MonitoringJob.ExecutionMode.STANDARD,
                applicationIds, pageable);
    }

    @Transactional(readOnly = true)
    public MonitoringJob findById(Long id)
    {
        MonitoringJob job = monitoringJobRepository.findByIdForDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + id));
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("This job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_VIEW");
        return job;
    }

    @Transactional(readOnly = true)
    public MonitoringJob findByIdForConfiguration(Long id) {
        MonitoringJob job = monitoringJobRepository.findByIdForDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + id));
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("This job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_CONFIG");
        return job;
    }

    @Transactional
    public MonitoringJob save(MonitoringJob job)
    {
        if (job.getApplication() == null || job.getApplication().getId() == null)
            throw new IllegalArgumentException("A monitoring job must be assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_CONFIG");
        if (job.getEnvironment() == null || job.getEnvironment().getApplication() == null
                || !job.getApplication().getId().equals(job.getEnvironment().getApplication().getId()))
            throw new IllegalArgumentException("The selected environment must belong to the selected application.");
        accessService.assertCanAccessEnvironment(job.getApplication().getId(),
                job.getEnvironment().getId(), "MONITORING_CONFIG");
        if (job.getMachine() != null) {
            if (job.getMachine().getEnvironment() == null
                    || !job.getEnvironment().getId().equals(job.getMachine().getEnvironment().getId())) {
                throw new IllegalArgumentException(
                        "The selected machine must belong to the monitoring job's selected environment.");
            }
        }
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

        if (job.getApplication() != null)
            accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_CONFIG");
        String jobName = job.getName();

        monitoringJobRepository.delete(job);
        monitoringSchedulerService.cancelJob(id);

        return jobName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        MonitoringJob job = findByIdForConfiguration(id);
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("This job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_CONFIG");
        job.setEnabled(!job.isEnabled());
        monitoringJobRepository.save(job);
        monitoringSchedulerService.refreshJob(job.getId());
    }
}
