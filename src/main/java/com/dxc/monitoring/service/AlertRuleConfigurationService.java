package com.dxc.monitoring.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.web.AlertRuleForm;

/**
 * Saves an alert rule and its rule-specific notification routes atomically.
 * Keeping this transaction behind a service proxy ensures commit failures are
 * raised to the controller, where they can be handled without masking the cause.
 */
@Service
public class AlertRuleConfigurationService {
    private final AlertRuleManagementService ruleManagementService;
    private final AlertRuleNotificationRouteService notificationRouteService;

    public AlertRuleConfigurationService(AlertRuleManagementService ruleManagementService,
            AlertRuleNotificationRouteService notificationRouteService) {
        this.ruleManagementService = ruleManagementService;
        this.notificationRouteService = notificationRouteService;
    }

    @Transactional
    public AlertRule save(AlertRuleForm form, Long userId) {
        AlertRule savedRule = ruleManagementService.save(form, userId);
        notificationRouteService.replaceRoutes(savedRule.getId(), form.getNotificationRoutesJson());
        return savedRule;
    }
}
