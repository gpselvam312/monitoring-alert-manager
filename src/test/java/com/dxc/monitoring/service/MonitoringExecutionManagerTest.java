package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.executor.MonitoringExecutorFactory;
import com.dxc.monitoring.service.executor.MonitoringResultNormalizer;

class MonitoringExecutionManagerTest {
    private MonitoringExecutionService executionService;
    private MonitoringJobRepository jobRepository;
    private MonitoringExecutorFactory executorFactory;
    private MonitoringExecutionManager manager;

    @BeforeEach
    void setUp() {
        executionService = mock(MonitoringExecutionService.class);
        jobRepository = mock(MonitoringJobRepository.class);
        executorFactory = mock(MonitoringExecutorFactory.class);
        manager = new MonitoringExecutionManager(executorFactory, executionService,
                mock(MonitoringResultNormalizer.class), mock(AlertEvaluationQueueService.class), jobRepository);
    }

    @Test
    void preventsCrossInstanceDuplicateExecutionWhenDatabaseClaimIsHeld() {
        MonitoringJob job = new MonitoringJob();
        job.setId(73L);
        job.setEnabled(true);
        job.setAllowConcurrentExecution(false);
        when(jobRepository.tryClaimExecution(eq(73L), anyString(), anyInt())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> manager.execute(job));
        verify(executionService, never()).startExecution(any(), anyInt(), any());
        verify(jobRepository, never()).releaseExecutionClaim(any(), anyString());
    }

    @Test
    void claimsAreNotUsedWhenConcurrentExecutionIsExplicitlyAllowed() {
        MonitoringJob job = new MonitoringJob();
        job.setId(74L);
        job.setEnabled(true);
        job.setAllowConcurrentExecution(true);
        job.setType(MonitoringJob.MonitorType.SCRIPT);
        when(executorFactory.getExecutor(MonitoringJob.MonitorType.SCRIPT))
                .thenThrow(new IllegalStateException("executor unavailable for test"));
        assertThrows(IllegalStateException.class, () -> manager.execute(job));
        verifyNoInteractions(jobRepository);
    }
}
