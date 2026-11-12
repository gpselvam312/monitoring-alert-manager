package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertEvaluationRequest;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.repository.AlertEvaluationRequestRepository;

@Service
public class AlertEvaluationQueueService {
    private final AlertEvaluationRequestRepository repository;

    public AlertEvaluationQueueService(AlertEvaluationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enqueue(MonitoringExecution execution) {
        if (execution == null || execution.getId() == null) {
            return;
        }
        AlertEvaluationRequest request = new AlertEvaluationRequest();
        request.setExecution(execution);
        request.setStatus(AlertEvaluationRequest.RequestStatus.PENDING);
        request.setAttemptCount(0);
        request.setNextAttemptAt(OffsetDateTime.now());
        repository.save(request);
    }

    @Transactional
    public List<ClaimedRequest> claimDueRequests(int batchSize, int maxAttempts) {
        List<AlertEvaluationRequest> requests = repository.lockDueRequests(batchSize, maxAttempts);
        OffsetDateTime now = OffsetDateTime.now();
        return requests.stream().map(request -> {
            request.setStatus(AlertEvaluationRequest.RequestStatus.PROCESSING);
            request.setAttemptCount(request.getAttemptCount() + 1);
            request.setLockedAt(now);
            return new ClaimedRequest(request.getId(), request.getExecution().getId(), request.getAttemptCount());
        }).toList();
    }

    @Transactional
    public void markCompleted(Long requestId) {
        AlertEvaluationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Alert evaluation request not found."));
        request.setStatus(AlertEvaluationRequest.RequestStatus.COMPLETED);
        request.setProcessedAt(OffsetDateTime.now());
        request.setLockedAt(null);
        request.setLastError(null);
    }

    @Transactional
    public void markFailed(Long requestId, int attemptCount, int maxAttempts, String errorMessage) {
        AlertEvaluationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Alert evaluation request not found."));
        request.setStatus(AlertEvaluationRequest.RequestStatus.FAILED);
        request.setLockedAt(null);
        request.setLastError(sanitize(errorMessage));
        if (attemptCount >= maxAttempts) {
            request.setNextAttemptAt(OffsetDateTime.now());
        } else {
            long delaySeconds = Math.min(300L, 1L << Math.min(attemptCount, 8));
            request.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Alert evaluation failed.";
        }
        String value = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record ClaimedRequest(Long requestId, Long executionId, int attemptCount) {}
}
