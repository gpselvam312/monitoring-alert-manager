package com.dxc.monitoring.service.executor;

import java.util.Map;

import com.dxc.monitoring.entity.MonitoringJob;

public interface MonitoringExecutor
{

    MonitoringJob.MonitorType getType();

    MonitoringExecutionResult execute(MonitoringJob job);

    default MonitoringExecutionResult execute(MonitoringJob job, Map<String, String> runtimeParameters)
    {
        return execute(job);
    }
}