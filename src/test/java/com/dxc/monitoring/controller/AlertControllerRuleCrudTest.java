package com.dxc.monitoring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleEvaluationRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.repository.UserAlertStateRepository;
import com.dxc.monitoring.service.AlertRuleConfigurationService;
import com.dxc.monitoring.service.AlertRuleFieldDiscoveryService;
import com.dxc.monitoring.service.AlertRuleManagementService;
import com.dxc.monitoring.service.AlertRuleNotificationRouteService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

class AlertControllerRuleCrudTest {
    private AlertRepository alertRepository;
    private AlertRuleRepository ruleRepository;
    private AlertRuleEvaluationRepository evaluationRepository;
    private DashboardAccessService accessService;
    private AlertController controller;
    private AlertRule rule;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        AlertHistoryRepository historyRepository = mock(AlertHistoryRepository.class);
        ruleRepository = mock(AlertRuleRepository.class);
        evaluationRepository = mock(AlertRuleEvaluationRepository.class);
        MonitoringJobRepository jobRepository = mock(MonitoringJobRepository.class);
        AlertRuleManagementService managementService = mock(AlertRuleManagementService.class);
        AlertRuleConfigurationService configurationService = mock(AlertRuleConfigurationService.class);
        accessService = mock(DashboardAccessService.class);
        UserAlertStateRepository userAlertStateRepository = mock(UserAlertStateRepository.class);
        NotificationPolicyRepository notificationPolicyRepository = mock(NotificationPolicyRepository.class);
        AlertRuleFieldDiscoveryService fieldDiscoveryService = mock(AlertRuleFieldDiscoveryService.class);
        AlertRuleNotificationRouteService routeService = mock(AlertRuleNotificationRouteService.class);

        controller = new AlertController(alertRepository, historyRepository, ruleRepository,
                evaluationRepository, jobRepository, managementService, configurationService,
                accessService, userAlertStateRepository, notificationPolicyRepository,
                fieldDiscoveryService, routeService);

        rule = mock(AlertRule.class);
        MonitoringJob job = mock(MonitoringJob.class);
        Application application = mock(Application.class);
        when(rule.getMonitoringJob()).thenReturn(job);
        when(job.getApplication()).thenReturn(application);
        when(application.getId()).thenReturn(42L);
        when(ruleRepository.findByIdWithJob(9L)).thenReturn(Optional.of(rule));
    }

    @Test
    void deletesRuleWhenNoAlertOrEvaluationHistoryExists() {
        when(alertRepository.existsByAlertRuleId(9L)).thenReturn(false);
        when(evaluationRepository.existsByAlertRuleId(9L)).thenReturn(false);
        RedirectAttributes flash = new RedirectAttributesModelMap();

        assertEquals("redirect:/alerts/rules", controller.deleteRule(9L, flash));

        verify(accessService).assertCanAccessApplication(42L, "ALERT_CONFIG");
        verify(ruleRepository).delete(rule);
        assertEquals("Alert rule deleted successfully.", flash.getFlashAttributes().get("successMessage"));
    }

    @Test
    void preventsDeletionWhenAlertHistoryExists() {
        when(alertRepository.existsByAlertRuleId(9L)).thenReturn(true);
        RedirectAttributes flash = new RedirectAttributesModelMap();

        assertEquals("redirect:/alerts/rules", controller.deleteRule(9L, flash));

        verify(ruleRepository, never()).delete(rule);
        assertEquals("This rule has alert or evaluation history and cannot be deleted without losing audit history. Disable it instead.",
                flash.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void preventsDeletionWhenEvaluationHistoryExists() {
        when(alertRepository.existsByAlertRuleId(9L)).thenReturn(false);
        when(evaluationRepository.existsByAlertRuleId(9L)).thenReturn(true);
        RedirectAttributes flash = new RedirectAttributesModelMap();

        controller.deleteRule(9L, flash);

        verify(ruleRepository, never()).delete(rule);
        assertEquals("This rule has alert or evaluation history and cannot be deleted without losing audit history. Disable it instead.",
                flash.getFlashAttributes().get("errorMessage"));
    }
}
