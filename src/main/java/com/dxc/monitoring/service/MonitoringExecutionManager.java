package com.dxc.monitoring.service;

import org.springframework.stereotype.Service;
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

    public MonitoringExecutionManager(MonitoringExecutorFactory executorFactory,
            MonitoringExecutionService executionService)
    {

        this.executorFactory = executorFactory;
        this.executionService = executionService;
    }

    @Transactional
    public MonitoringExecution execute(MonitoringJob job)
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
            executionService.saveResult(execution, result.getResultType(), result.getResultStatus(), result.getValue(),
                    result.getUnit(), result.getMessage(), result.getResultData(), result.getRawOutput());
            return execution;
        } catch (Exception ex)
        {
            ex.printStackTrace();

            return executionService.failExecution(execution, MonitoringExecution.ExecutionStatus.ERROR,
                    ex.getMessage());
        }
    }
}