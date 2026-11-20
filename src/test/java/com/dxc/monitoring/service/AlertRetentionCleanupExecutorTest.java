package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.AlertRetentionPolicyRepository;

import tools.jackson.databind.ObjectMapper;

class AlertRetentionCleanupExecutorTest {
    private AlertRetentionPolicyRepository policyRepository;
    private JdbcTemplate jdbcTemplate;
    private AlertRetentionCleanupExecutor executor;

    @BeforeEach
    void setUp() {
        policyRepository = mock(AlertRetentionPolicyRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        executor = new AlertRetentionCleanupExecutor(policyRepository, jdbcTemplate, mock(ObjectMapper.class));
    }

    @Test
    void notificationDeliveryPreviewAppliesApplicationEnvironmentAndAlertTypeScope() {
        AlertRetentionPolicy policy = scopedPolicy(AlertRetentionPolicy.DataType.NOTIFICATION_DELIVERIES);
        policy.setAlertType("MONITORING_JOB");
        when(policyRepository.findById(8L)).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);

        executor.preview(8L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("JOIN ra_fcb.alerts a ON a.id = d.alert_id"));
        assertTrue(sql.getValue().contains("j.application_id = ?"));
        assertTrue(sql.getValue().contains("j.environment_id = ?"));
        assertTrue(sql.getValue().contains("a.alert_type = ?"));
        assertTrue(sql.getValue().contains("d.status IN ('SENT', 'SKIPPED')"));
    }

    @Test
    void ruleEvaluationPreviewAppliesApplicationAndEnvironmentScopeAndPreservesLatestEvaluation() {
        AlertRetentionPolicy policy = scopedPolicy(AlertRetentionPolicy.DataType.RULE_EVALUATIONS);
        when(policyRepository.findById(9L)).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(4L);

        executor.preview(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("JOIN ra_fcb.alert_rules r ON r.id = e.alert_rule_id"));
        assertTrue(sql.getValue().contains("j.application_id = ?"));
        assertTrue(sql.getValue().contains("j.environment_id = ?"));
        assertTrue(sql.getValue().contains("newer.alert_rule_id = e.alert_rule_id"));
    }

    private AlertRetentionPolicy scopedPolicy(AlertRetentionPolicy.DataType dataType) {
        Application application = mock(Application.class);
        Environment environment = mock(Environment.class);
        when(application.getId()).thenReturn(11L);
        when(environment.getId()).thenReturn(22L);
        AlertRetentionPolicy policy = new AlertRetentionPolicy();
        policy.setDataType(dataType);
        policy.setRetentionDays(90);
        policy.setApplication(application);
        policy.setEnvironment(environment);
        policy.setAlertType("ALL");
        return policy;
    }

    @Test
    void notificationDeliveryPreviewUsesSameEligibilityAsCleanup() {
        AlertRetentionPolicy policy = new AlertRetentionPolicy();
        policy.setDataType(AlertRetentionPolicy.DataType.NOTIFICATION_DELIVERIES);
        policy.setRetentionDays(90);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(3L);

        AlertRetentionCleanupExecutor.Preview preview = executor.preview(7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertEquals(3L, preview.candidateCount());
        assertTrue(sql.getValue().contains("d.status IN ('SENT', 'SKIPPED')"));
        assertTrue(sql.getValue().contains("d.status = 'FAILED' AND d.attempt_count >= 8"));
        assertFalse(sql.getValue().contains("d.status NOT IN ('PENDING', 'SENDING')"));
    }
}
