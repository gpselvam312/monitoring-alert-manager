package com.dxc.monitoring.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.service.executor.MonitoringExecutionResult;
import com.dxc.monitoring.service.executor.MonitoringExecutor;
import com.dxc.monitoring.service.executor.MonitoringExecutorFactory;

@Service
public class MonitoringExecutionManager
{

    private final MonitoringExecutorFactory executorFactory;
    private final MonitoringExecutionService executionService;
    private final ConcurrentHashMap<Long, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    public MonitoringExecutionManager(MonitoringExecutorFactory executorFactory,
            MonitoringExecutionService executionService)
    {

        this.executorFactory = executorFactory;
        this.executionService = executionService;
    }

    @Transactional
    public MonitoringExecution execute(MonitoringJob job)
    {
        if (job == null || job.getId() == null)
        {
            throw new IllegalArgumentException("Monitoring job is required.");
        }

        if (job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING)
        {
            throw new IllegalStateException("Streaming jobs must be started from the Streaming Jobs page.");
        }

        if (!job.isEnabled())
        {
            throw new IllegalStateException("Monitoring job is disabled.");
        }

        ReentrantLock lock = jobLocks.computeIfAbsent(job.getId(), ignored -> new ReentrantLock());

        if (!job.isAllowConcurrentExecution() && !lock.tryLock())
        {
            throw new IllegalStateException("Monitoring job is already running.");
        }

        try
        {
            MonitoringExecutor executor = executorFactory.getExecutor(job.getType());
        MonitoringExecution execution = executionService.startExecution(job, 1);
        try
        {
            MonitoringExecutionResult result = executor.execute(job);
            if (result.getErrorMessage() != null)
            {
                execution.setErrorMessage(result.getErrorMessage());
            }
            execution = executionService.completeExecution(execution, result.getExecutionStatus());
            if (job.isStoreResult())
            {
                executionService.saveResult(execution, result.getResultType(), result.getResultStatus(),
                        result.getValue(), result.getUnit(), result.getMessage(), result.getResultData(),
                        result.getRawOutput());
            }
            return execution;
        } catch (Exception ex)
        {
            ex.printStackTrace();

            return executionService.failExecution(execution, MonitoringExecution.ExecutionStatus.ERROR,
                    ex.getMessage());
        }
        finally
        {
            if (!job.isAllowConcurrentExecution() && lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }

            if (!lock.hasQueuedThreads() && !lock.isLocked())
            {
                jobLocks.remove(job.getId(), lock);
            }
        }
    }
}