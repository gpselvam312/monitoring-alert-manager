package com.dxc.monitoring.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.executor.MonitoringExecutionResult;
import com.dxc.monitoring.service.executor.MonitoringExecutor;
import com.dxc.monitoring.service.executor.MonitoringExecutorFactory;
import com.dxc.monitoring.service.executor.MonitoringResultNormalizer;

@Service
public class MonitoringExecutionManager
{
    private final MonitoringExecutorFactory executorFactory;
    private final MonitoringExecutionService executionService;
    private final MonitoringResultNormalizer resultNormalizer;
    private final AlertEvaluationQueueService alertEvaluationQueueService;
    private final MonitoringJobRepository monitoringJobRepository;
    private final ConcurrentHashMap<Long, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    public MonitoringExecutionManager(MonitoringExecutorFactory executorFactory,
            MonitoringExecutionService executionService, MonitoringResultNormalizer resultNormalizer,
            AlertEvaluationQueueService alertEvaluationQueueService,
            MonitoringJobRepository monitoringJobRepository)
    {
        this.executorFactory = executorFactory;
        this.executionService = executionService;
        this.resultNormalizer = resultNormalizer;
        this.alertEvaluationQueueService = alertEvaluationQueueService;
        this.monitoringJobRepository = monitoringJobRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonitoringExecution execute(MonitoringJob job)
    {
        return execute(job, Map.of(), MonitoringExecution.TriggerSource.MANUAL);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonitoringExecution execute(MonitoringJob job, MonitoringExecution.TriggerSource triggerSource)
    {
        return execute(job, Map.of(), triggerSource);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonitoringExecution execute(MonitoringJob job, Map<String, String> runtimeParameters)
    {
        return execute(job, runtimeParameters, MonitoringExecution.TriggerSource.MANUAL);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonitoringExecution execute(MonitoringJob job, Map<String, String> runtimeParameters,
            MonitoringExecution.TriggerSource triggerSource)
    {
        if (job == null || job.getId() == null)
            throw new IllegalArgumentException("Monitoring job is required.");

        if (job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING)
            throw new IllegalStateException("Streaming jobs must be started from the Streaming Jobs page.");

        if (!job.isEnabled())
            throw new IllegalStateException("Monitoring job is disabled.");

        ReentrantLock lock = jobLocks.computeIfAbsent(job.getId(), ignored -> new ReentrantLock());
        boolean lockAcquired = false;
        if (!job.isAllowConcurrentExecution())
        {
            lockAcquired = lock.tryLock();
            if (!lockAcquired)
                throw new IllegalStateException("Monitoring job is already running.");
        }

        String claimToken = null;
        try
        {
            if (!job.isAllowConcurrentExecution()) {
                String candidateToken = UUID.randomUUID().toString();
                int timeoutSeconds = job.getTimeoutSeconds() == null ? 300 : Math.max(1, job.getTimeoutSeconds());
                int leaseSeconds = (int) Math.min(Integer.MAX_VALUE, (long) timeoutSeconds + 120L);
                if (monitoringJobRepository.tryClaimExecution(job.getId(), candidateToken, leaseSeconds) != 1)
                    throw new IllegalStateException("Monitoring job is already running on another application instance.");
                claimToken = candidateToken;
            }
            MonitoringExecutor executor = executorFactory.getExecutor(job.getType());
            MonitoringExecution execution = executionService.startExecution(job, 1,
                    triggerSource == null ? MonitoringExecution.TriggerSource.UNKNOWN : triggerSource);
            try
            {
                MonitoringExecutionResult result = executor.execute(job, runtimeParameters == null ? Map.of() : runtimeParameters);
                if (result.getErrorMessage() != null)
                    execution.setErrorMessage(result.getErrorMessage());

                execution = executionService.completeExecution(execution, result.getExecutionStatus());
                if (job.isStoreResult())
                {
                    if (result.getResultData() == null || result.getResultData().isBlank())
                    {
                        MonitoringResultNormalizer.NormalizedResult normalized = resultNormalizer.normalize(
                                result.getRawOutput(), result.getExecutionStatus(), result.getResultStatus(),
                                result.getMessage(), execution.getStartedAt(), java.time.OffsetDateTime.now(), job.getResultParserConfig());
                        result.setResultData(normalized.json());
                        result.setResultType(normalized.resultType());
                        result.setResultStatus(normalized.resultStatus());
                        result.setMessage(normalized.message());
                    }

                    String resultData = resultNormalizer.attachExecutionId(result.getResultData(), execution.getId());
                    executionService.saveResult(execution, result.getResultType(), result.getResultStatus(),
                            result.getValue(), result.getUnit(), result.getMessage(), resultData, result.getRawOutput());
                }
            }
            catch (Exception exception)
            {
                MonitoringExecution failedExecution = executionService.failExecution(execution,
                        MonitoringExecution.ExecutionStatus.ERROR, exception.getMessage());
                alertEvaluationQueueService.enqueue(failedExecution);
                return failedExecution;
            }

            alertEvaluationQueueService.enqueue(execution);
            return execution;
        }
        finally
        {
            if (claimToken != null) {
                try {
                    monitoringJobRepository.releaseExecutionClaim(job.getId(), claimToken);
                } catch (RuntimeException releaseException) {
                    // The bounded claim lease safely expires if this instance cannot release it.
                }
            }
            if (lockAcquired) {
                lock.unlock();
                jobLocks.remove(job.getId(), lock);
            }
        }
    }
}
