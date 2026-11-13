package com.dxc.monitoring.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertCleanupRun;
import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.AlertCleanupRunRepository;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;

@Service
public class AlertRetentionService {
    private final AlertRetentionPolicyRepository policyRepository;
    private final AlertCleanupRunRepository runRepository;
    private final AlertRetentionCleanupExecutor cleanupExecutor;
    private final AlertCleanupAuditService auditService;
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;

    public AlertRetentionService(AlertRetentionPolicyRepository policyRepository,
            AlertCleanupRunRepository runRepository, AlertRetentionCleanupExecutor cleanupExecutor,
            AlertCleanupAuditService auditService, ApplicationRepository applicationRepository,
            EnvironmentRepository environmentRepository) {
        this.policyRepository = policyRepository;
        this.runRepository = runRepository;
        this.cleanupExecutor = cleanupExecutor;
        this.auditService = auditService;
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public AlertRetentionPolicy createPolicy(String dataTypeValue, int retentionDays,
            boolean enabled, String description, Long applicationId, Long environmentId,
            String alertTypeValue, Long userId) {
        AlertRetentionPolicy.DataType dataType;
        try {
            dataType = AlertRetentionPolicy.DataType.valueOf(dataTypeValue);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Choose a supported retention data type.");
        }
        if (retentionDays < 1 || retentionDays > 36500) {
            throw new IllegalArgumentException("Retention must be between 1 and 36500 days.");
        }
        String alertType = alertTypeValue == null || alertTypeValue.isBlank()
                ? "ALL" : alertTypeValue.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "MONITORING_JOB").contains(alertType)) {
            throw new IllegalArgumentException("Choose a supported alert type.");
        }
        if (environmentId != null && applicationId == null) {
            throw new IllegalArgumentException("Select an application before selecting an environment.");
        }
        Application application = applicationId == null ? null : applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Selected application was not found."));
        Environment environment = environmentId == null ? null : environmentRepository.findById(environmentId)
                .orElseThrow(() -> new IllegalArgumentException("Selected environment was not found."));
        if (environment != null && (environment.getApplication() == null
                || !environment.getApplication().getId().equals(applicationId))) {
            throw new IllegalArgumentException("The selected environment does not belong to the selected application.");
        }
        if (policyRepository.existsByDataTypeAndApplication_IdAndEnvironment_IdAndAlertType(
                dataType, applicationId, environmentId, alertType)) {
            throw new IllegalArgumentException("A retention policy already exists for this exact scope.");
        }

        AlertRetentionPolicy policy = new AlertRetentionPolicy();
        policy.setDataType(dataType);
        policy.setRetentionDays(retentionDays);
        policy.setEnabled(enabled);
        policy.setDescription(description == null || description.isBlank() ? null : description.trim());
        policy.setApplication(application);
        policy.setEnvironment(environment);
        policy.setAlertType(alertType);
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
