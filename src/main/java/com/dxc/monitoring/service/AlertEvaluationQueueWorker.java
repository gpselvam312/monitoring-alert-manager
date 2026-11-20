package com.dxc.monitoring.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.config.BackgroundWorkReadiness;

@Component
public class AlertEvaluationQueueWorker {
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationQueueWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 8;
    private final AlertEvaluationQueueService queueService;
    private final AlertEvaluationService evaluationService;
    private final BackgroundWorkReadiness readiness;

    public AlertEvaluationQueueWorker(AlertEvaluationQueueService queueService,
            AlertEvaluationService evaluationService, BackgroundWorkReadiness readiness) {
        this.queueService = queueService;
        this.evaluationService = evaluationService;
        this.readiness = readiness;
    }

    @Scheduled(fixedDelayString = "${alerts.evaluation.poll-interval-ms:2000}")
    public void poll() {
        if (!readiness.isReady()) return;
        List<AlertEvaluationQueueService.ClaimedRequest> requests =
                queueService.claimDueRequests(BATCH_SIZE, MAX_ATTEMPTS);
        for (AlertEvaluationQueueService.ClaimedRequest request : requests) {
            try {
                evaluationService.evaluateExecution(request.executionId());
                queueService.markCompleted(request.requestId());
            } catch (Exception exception) {
                log.error("Alert evaluation request {} failed on attempt {} exceptionType={}.",
                        request.requestId(), request.attemptCount(), exception.getClass().getSimpleName());
                try {
                    queueService.markFailed(request.requestId(), request.attemptCount(), MAX_ATTEMPTS,
                            exception.getClass().getSimpleName());
                } catch (RuntimeException stateException) {
                    log.error("Unable to persist failure state for alert evaluation request {} exceptionType={}",
                            request.requestId(), stateException.getClass().getSimpleName());
                }
            }
        }
    }
}
