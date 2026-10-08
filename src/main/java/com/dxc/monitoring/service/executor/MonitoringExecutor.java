package com.dxc.monitoring.service.executor;

import com.dxc.monitoring.entity.MonitoringJob;

public interface MonitoringExecutor
{

    MonitoringJob.MonitorType getType();

    MonitoringExecutionResult execute(MonitoringJob job);
}