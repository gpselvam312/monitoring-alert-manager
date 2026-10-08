package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;

@Service
public class MonitoringJobService
{

    private final MonitoringJobRepository monitoringJobRepository;

    public MonitoringJobService(MonitoringJobRepository monitoringJobRepository)
    {
        this.monitoringJobRepository = monitoringJobRepository;
    }

    @Transactional(readOnly = true)
    public Page<MonitoringJob> findAll(String search, Pageable pageable)
    {
        return monitoringJobRepository.findAllForList(search, pageable);
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
        return monitoringJobRepository.save(job);
    }

    @Transactional
    public String delete(Long id)
    {
        MonitoringJob job = monitoringJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + id));

        String jobName = job.getName();

        monitoringJobRepository.delete(job);

        return jobName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        MonitoringJob job = findById(id);
        job.setEnabled(!job.isEnabled());
        monitoringJobRepository.save(job);
    }
}
