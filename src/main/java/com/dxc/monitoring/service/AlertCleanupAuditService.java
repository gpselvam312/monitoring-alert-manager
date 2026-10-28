package com.dxc.monitoring.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertCleanupRun;
import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.repository.AlertCleanupRunRepository;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;

@Service
public class AlertCleanupAuditService {
    private final AlertCleanupRunRepository runRepository;
    private final AlertRetentionPolicyRepository policyRepository;

    public AlertCleanupAuditService(AlertCleanupRunRepository runRepository,
            AlertRetentionPolicyRepository policyRepository) {
        this.runRepository = runRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlertCleanupRun start(Long policyId, Long userId) {
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Retention policy was not found."));
        AlertCleanupRun run = new AlertCleanupRun();
        run.setPolicy(policy);
        run.setStatus(AlertCleanupRun.RunStatus.RUNNING);
        run.setStartedAt(OffsetDateTime.now());
        run.setRequestedBy(userId);
        run.setRecordsExamined(0L);
        run.setRecordsDeleted(0L);
        run.setDetails("{}");
        return runRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(Long runId, AlertRetentionCleanupExecutor.CleanupResult result) {
        AlertCleanupRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Cleanup audit record was not found."));
        run.setStatus(AlertCleanupRun.RunStatus.COMPLETED);
        run.setCompletedAt(OffsetDateTime.now());
        run.setRecordsExamined(result.recordsExamined());
        run.setRecordsDeleted(result.recordsDeleted());
        run.setDetails(result.details());
        run.setErrorMessage(null);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long runId, String error) {
        AlertCleanupRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Cleanup audit record was not found."));
        run.setStatus(AlertCleanupRun.RunStatus.FAILED);
        run.setCompletedAt(OffsetDateTime.now());
        String message = error == null || error.isBlank() ? "Retention cleanup failed." : error.replaceAll("[\\r\\n\\t]+", " ").trim();
        run.setErrorMessage(message.length() > 2000 ? message.substring(0, 2000) : message);
        runRepository.save(run);
    }
}
