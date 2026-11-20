package com.dxc.monitoring.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Service
public class MonitoringExecutionService
{

    private final MonitoringExecutionRepository executionRepository;
    private final MonitoringResultRepository resultRepository;
    private final DashboardAccessService accessService;
    private final MonitoringJobRepository jobRepository;

    public MonitoringExecutionService(MonitoringExecutionRepository executionRepository,
            MonitoringResultRepository resultRepository, DashboardAccessService accessService,
            MonitoringJobRepository jobRepository)
    {
        this.executionRepository = executionRepository;
        this.resultRepository = resultRepository;
        this.accessService = accessService;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public MonitoringExecution startExecution(MonitoringJob monitoringJob, int attemptNumber,
            MonitoringExecution.TriggerSource triggerSource)
    {

        MonitoringExecution execution = new MonitoringExecution();

        execution.setMonitoringJob(monitoringJob);
        execution.setStartedAt(OffsetDateTime.now());
        execution.setStatus(MonitoringExecution.ExecutionStatus.RUNNING);
        execution.setAttemptNumber(attemptNumber);
        execution.setTriggerSource(triggerSource);

        return executionRepository.save(execution);
    }

    @Transactional
    public MonitoringExecution completeExecution(MonitoringExecution execution,
            MonitoringExecution.ExecutionStatus status)
    {

        OffsetDateTime completedAt = OffsetDateTime.now();

        execution.setCompletedAt(completedAt);
        execution.setStatus(status);

        if (execution.getStartedAt() != null)
        {
            execution.setDurationMs(Duration.between(execution.getStartedAt(), completedAt).toMillis());
        }

        return executionRepository.save(execution);
    }

    @Transactional
    public MonitoringExecution failExecution(MonitoringExecution execution, MonitoringExecution.ExecutionStatus status,
            String errorMessage)
    {

        execution.setErrorMessage(errorMessage);

        return completeExecution(execution, status);
    }

    @Transactional
    public MonitoringResult saveResult(MonitoringExecution execution, MonitoringResult.ResultType resultType,
            MonitoringResult.ResultStatus status, String value, String unit, String message, String resultData,
            String rawOutput)
    {

        MonitoringResult result = new MonitoringResult();

        result.setExecution(execution);
        result.setResultType(resultType);
        result.setStatus(status);
        result.setValue(value);
        result.setUnit(unit);
        result.setMessage(message);
        result.setResultData(resultData);
        result.setRawOutput(rawOutput);

        return resultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public List<MonitoringExecution> findByJobId(Long monitoringJobId)
    {
        assertJobAccess(monitoringJobId);
        return executionRepository.findByMonitoringJobIdOrderByStartedAtDesc(monitoringJobId);
    }

    @Transactional(readOnly = true)
    public List<MonitoringResult> findResultsByExecutionId(Long executionId)
    {
        findById(executionId);
        return resultRepository.findAllByExecutionId(executionId);
    }

    @Transactional(readOnly = true)
    public Page<MonitoringExecution> findByJobId(Long monitoringJobId, String search, Pageable pageable)
    {
        assertJobAccess(monitoringJobId);
        return executionRepository.findByMonitoringJobId(monitoringJobId, search, pageable);
    }

    @Transactional(readOnly = true)
    public MonitoringResult.ResultStatus getOverallResultStatus(Long executionId)
    {
        List<MonitoringResult> results = resultRepository.findAllByExecutionId(executionId);

        if (results.isEmpty())
        {
            return null;
        }

        if (results.stream().anyMatch(r -> r.getStatus() == MonitoringResult.ResultStatus.FAILED))
        {
            return MonitoringResult.ResultStatus.FAILED;
        }

        if (results.stream().anyMatch(r -> r.getStatus() == MonitoringResult.ResultStatus.WARNING))
        {
            return MonitoringResult.ResultStatus.WARNING;
        }

        return MonitoringResult.ResultStatus.OK;
    }

    @Transactional(readOnly = true)
    public Map<Long, MonitoringResult.ResultStatus> findOverallResultStatuses(List<MonitoringExecution> executions)
    {
        if (executions == null || executions.isEmpty())
        {
            return Collections.emptyMap();
        }

        List<Long> executionIds = executions.stream().map(MonitoringExecution::getId).toList();

        List<MonitoringResult> results = resultRepository.findByExecutionIds(executionIds);

        Map<Long, MonitoringResult.ResultStatus> statuses = new HashMap<>();

        for (MonitoringResult result : results)
        {
            Long executionId = result.getExecution().getId();

            MonitoringResult.ResultStatus currentStatus = statuses.get(executionId);

            MonitoringResult.ResultStatus resultStatus = result.getStatus();

            if (resultStatus == null)
            {
                continue;
            }

            if (currentStatus == MonitoringResult.ResultStatus.FAILED
                    || resultStatus == MonitoringResult.ResultStatus.FAILED)
            {
                statuses.put(executionId, MonitoringResult.ResultStatus.FAILED);
            } else if (currentStatus == MonitoringResult.ResultStatus.WARNING
                    || resultStatus == MonitoringResult.ResultStatus.WARNING)
            {
                statuses.put(executionId, MonitoringResult.ResultStatus.WARNING);
            } else
            {
                statuses.put(executionId, MonitoringResult.ResultStatus.OK);
            }
        }

        return statuses;
    }

    @Transactional(readOnly = true)
    public MonitoringExecution findById(Long executionId)
    {
        MonitoringExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        assertJobAccess(execution.getMonitoringJob().getId());
        return execution;
    }

    private void assertJobAccess(Long jobId)
    {
        MonitoringJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + jobId));
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("This job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public MonitoringExecution findByIdForJob(Long jobId, Long executionId)
    {
        MonitoringExecution execution = executionRepository.findByIdAndMonitoringJob_Id(executionId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found for monitoring job."));
        assertJobAccess(jobId);
        return execution;
    }
}