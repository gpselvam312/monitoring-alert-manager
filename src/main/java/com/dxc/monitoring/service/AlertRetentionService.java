package com.dxc.monitoring.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertCleanupRun;
import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.repository.AlertCleanupRunRepository;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;

@Service
public class AlertRetentionService {
    private final AlertRetentionPolicyRepository policyRepository;
    private final AlertCleanupRunRepository runRepository;
    private final AlertRetentionCleanupExecutor cleanupExecutor;
    private final AlertCleanupAuditService auditService;

    public AlertRetentionService(AlertRetentionPolicyRepository policyRepository,
            AlertCleanupRunRepository runRepository, AlertRetentionCleanupExecutor cleanupExecutor,
            AlertCleanupAuditService auditService) {
        this.policyRepository = policyRepository;
        this.runRepository = runRepository;
        this.cleanupExecutor = cleanupExecutor;
        this.auditService = auditService;
    }

    @Transactional
    public AlertRetentionPolicy createPolicy(String dataTypeValue, int retentionDays,
            boolean enabled, String description, Long userId) {
        AlertRetentionPolicy.DataType dataType;
        try { dataType = AlertRetentionPolicy.DataType.valueOf(dataTypeValue); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Choose a supported retention data type."); }
        if (retentionDays < 1 || retentionDays > 36500) {
            throw new IllegalArgumentException("Retention must be between 1 and 36500 days.");
        }
        if (policyRepository.existsByDataType(dataType)) {
            throw new IllegalArgumentException("A retention policy already exists for this data type. Edit or enable the existing policy.");
        }
        AlertRetentionPolicy policy = new AlertRetentionPolicy();
        policy.setDataType(dataType);
        policy.setRetentionDays(retentionDays);
        policy.setEnabled(enabled);
        policy.setDescription(description == null || description.isBlank() ? null : description.trim());
        policy.setCreatedBy(userId);
        policy.setUpdatedBy(userId);
        return policyRepository.save(policy);
    }

    @Transactional
    public AlertRetentionPolicy updatePolicy(Long policyId, int retentionDays, String description, Long userId) {
        if (retentionDays < 1 || retentionDays > 36500) {
            throw new IllegalArgumentException("Retention must be between 1 and 36500 days.");
        }
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Retention policy was not found."));
        policy.setRetentionDays(retentionDays);
        policy.setDescription(description == null || description.isBlank() ? null : description.trim());
        policy.setUpdatedBy(userId);
        return policyRepository.save(policy);
    }

    @Transactional
    public void togglePolicy(Long policyId, Long userId) {
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Retention policy was not found."));
        policy.setEnabled(!policy.isEnabled());
        policy.setUpdatedBy(userId);
        policyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public AlertRetentionCleanupExecutor.Preview preview(Long policyId) {
        return cleanupExecutor.preview(policyId);
    }

    public AlertCleanupRun runCleanup(Long policyId, Long userId) {
        AlertRetentionPolicy policy = policyRepository.findById(policyId)
                .filter(AlertRetentionPolicy::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Only an enabled retention policy can be run."));
        AlertCleanupRun run = auditService.start(policy.getId(), userId);
        try {
            AlertRetentionCleanupExecutor.CleanupResult result = cleanupExecutor.cleanup(policy.getId());
            auditService.completed(run.getId(), result);
            return runRepository.findById(run.getId())
                    .orElseThrow(() -> new IllegalStateException("Cleanup audit record could not be reloaded."));
        } catch (RuntimeException exception) {
            auditService.failed(run.getId(), exception.getMessage());
            throw new IllegalStateException("Retention cleanup failed. Review the cleanup audit record.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<AlertRetentionPolicy> findPolicies() {
        return policyRepository.findAllByOrderByDataTypeAsc();
    }
}
