package com.dxc.monitoring.service.executor;

import com.dxc.monitoring.entity.MonitoringJob;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MonitoringExecutorFactory
{

    private final Map<MonitoringJob.MonitorType, MonitoringExecutor> executors;

    public MonitoringExecutorFactory(List<MonitoringExecutor> executorList)
    {
        this.executors = new EnumMap<>(MonitoringJob.MonitorType.class);

        for (MonitoringExecutor executor : executorList)
        {
            executors.put(executor.getType(), executor);
        }
    }

    public MonitoringExecutor getExecutor(MonitoringJob.MonitorType type)
    {
        MonitoringExecutor executor = executors.get(type);

        if (executor == null)
        {
            throw new IllegalArgumentException("No monitoring executor registered for type: " + type);
        }

        return executor;
    }
}