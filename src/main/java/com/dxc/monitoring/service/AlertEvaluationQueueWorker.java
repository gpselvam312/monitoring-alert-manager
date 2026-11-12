package com.dxc.monitoring.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertEvaluationQueueWorker {
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationQueueWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 8;

    private final AlertEvaluationQueueService queueService;
    private final AlertEvaluationService evaluationService;

    public AlertEvaluationQueueWorker(AlertEvaluationQueueService queueService,
            AlertEvaluationService evaluationService) {
        this.queueService = queueService;
        this.evaluationService = evaluationService;
    }

    @Scheduled(fixedDelayString = "${alerts.evaluation.poll-interval-ms:2000}")
    public void poll() {
        List<AlertEvaluationQueueService.ClaimedRequest> requests =
                queueService.claimDueRequests(BATCH_SIZE, MAX_ATTEMPTS);
        for (AlertEvaluationQueueService.ClaimedRequest request : requests) {
            try {
                evaluationService.evaluateExecution(request.executionId());
                queueService.markCompleted(request.requestId());
            } catch (Exception exception) {
                log.error("Alert evaluation request {} failed on attempt {}.",
                        request.requestId(), request.attemptCount(), exception);
                queueService.markFailed(request.requestId(), request.attemptCount(), MAX_ATTEMPTS,
                        exception.getMessage());
            }
        }
    }
}
