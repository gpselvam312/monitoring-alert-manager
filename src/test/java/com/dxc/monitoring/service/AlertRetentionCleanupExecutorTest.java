package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(sql.getValue().contains("status IN ('SENT', 'SKIPPED')"));
        assertTrue(sql.getValue().contains("status = 'FAILED' AND attempt_count >= 8"));
        assertTrue(!sql.getValue().contains("status NOT IN ('PENDING', 'SENDING')"));
    }
}
