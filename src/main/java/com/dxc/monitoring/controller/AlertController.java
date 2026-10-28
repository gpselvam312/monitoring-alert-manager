package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.repository.UserAlertStateRepository;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.AlertRuleManagementService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.AlertRuleForm;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/alerts")
public class AlertController {
    private final AlertRepository alertRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertRuleRepository ruleRepository;
    private final MonitoringJobRepository jobRepository;
    private final AlertRuleManagementService ruleManagementService;
    private final DashboardAccessService accessService;
    private final UserAlertStateRepository userAlertStateRepository;
    private final NotificationPolicyRepository notificationPolicyRepository;

    public AlertController(AlertRepository alertRepository, AlertHistoryRepository historyRepository,
            AlertRuleRepository ruleRepository, MonitoringJobRepository jobRepository,
            AlertRuleManagementService ruleManagementService, DashboardAccessService accessService,
            UserAlertStateRepository userAlertStateRepository,
            NotificationPolicyRepository notificationPolicyRepository) {
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
        this.ruleManagementService = ruleManagementService;
        this.accessService = accessService;
        this.userAlertStateRepository = userAlertStateRepository;
        this.notificationPolicyRepository = notificationPolicyRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String activeAlerts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Model model) {
        Pageable pageable = pageable(page, size);
        List<Long> applicationIds = applicationIds("ALERT_VIEW");
        Page<Alert> alerts = applicationIds.isEmpty() ? Page.empty(pageable)
                : alertRepository.findByMonitoringJob_Application_IdInAndStatusOrderByLastOccurredAtDescIdDesc(
                        applicationIds, Alert.AlertStatus.ACTIVE, pageable);
        model.addAttribute("alerts", alerts);
        model.addAttribute("pageSize", pageable.getPageSize());
        model.addAttribute("currentPage", "active-alerts");
        return "alerts/active";
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String alertHistory(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Model model) {
        Pageable pageable = pageable(page, size);
        List<Long> applicationIds = applicationIds("ALERT_VIEW");
        Page<Alert> alerts = applicationIds.isEmpty() ? Page.empty(pageable)
                : alertRepository.findByMonitoringJob_Application_IdInAndStatusOrderByLastOccurredAtDescIdDesc(
                        applicationIds, Alert.AlertStatus.RESOLVED, pageable);
        model.addAttribute("alerts", alerts);
        model.addAttribute("pageSize", pageable.getPageSize());
        model.addAttribute("currentPage", "alert-history");
        return "alerts/history";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String alertDetails(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Authentication authentication, Model model) {
        List<Long> applicationIds = applicationIds("ALERT_VIEW");
        Alert alert = applicationIds.isEmpty() ? null
                : alertRepository.findByIdAndMonitoringJob_Application_IdIn(id, applicationIds).orElse(null);
        if (alert == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert was not found.");
        Long currentUserId = userId(authentication);
        if (currentUserId != null) userAlertStateRepository.markRead(currentUserId, id);
        Pageable pageable = pageable(page, size);
        Pageable historyPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AlertHistory> history = historyRepository.findByAlertIdOrderByOccurredAtDescIdDesc(id, historyPageable);
        model.addAttribute("alert", alert);
        model.addAttribute("history", history);
        model.addAttribute("pageSize", pageable.getPageSize());
        model.addAttribute("currentPage", "active-alerts");
        return "alerts/detail";
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String alertRules(Model model) {
        List<Long> applicationIds = applicationIds("ALERT_CONFIG");
        List<AlertRule> rules = applicationIds.isEmpty() ? List.of()
                : ruleRepository.findByMonitoringJob_Application_IdInOrderByIdDesc(applicationIds);
        model.addAttribute("rules", rules);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/rules";
    }

    @GetMapping("/rules/new")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String newRule(Model model) {
        AlertRuleForm form = new AlertRuleForm();
        addRuleFormModel(model, form);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/rule-form";
    }

    @GetMapping("/rules/{id}/edit")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String editRule(@PathVariable Long id, Model model) {
        AlertRule rule = ruleRepository.findByIdWithJob(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule was not found."));
        assertRuleAccess(rule, "ALERT_CONFIG");
        addRuleFormModel(model, ruleManagementService.toForm(rule));
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/rule-form";
    }

    @PostMapping("/rules/save")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String saveRule(@ModelAttribute AlertRuleForm form, Authentication authentication, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            if (form.getId() != null) {
                AlertRule existing = ruleRepository.findByIdWithJob(form.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Alert rule was not found."));
                assertRuleAccess(existing, "ALERT_CONFIG");
            }
            MonitoringJob job = jobRepository.findByIdForDetails(form.getMonitoringJobId())
                    .orElseThrow(() -> new IllegalArgumentException("Select an available monitoring job."));
            assertJobAccess(job, "ALERT_CONFIG");
            ruleManagementService.save(form, userId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Alert rule saved successfully.");
            return "redirect:/alerts/rules";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            addRuleFormModel(model, form);
            model.addAttribute("errorMessage", exception instanceof DataIntegrityViolationException
                    ? "A rule with this name already exists for the selected monitoring job."
                    : safeMessage(exception.getMessage()));
            model.addAttribute("currentPage", "alert-rules");
            return "alerts/rule-form";
        }
    }

    @PostMapping("/rules/{id}/toggle")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String toggleRule(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        AlertRule rule = ruleRepository.findByIdWithJob(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule was not found."));
        assertRuleAccess(rule, "ALERT_CONFIG");
        rule.setEnabled(!rule.isEnabled());
        rule.setUpdatedBy(userId(authentication));
        ruleRepository.save(rule);
        redirectAttributes.addFlashAttribute("successMessage",
                rule.isEnabled() ? "Alert rule enabled." : "Alert rule disabled.");
        return "redirect:/alerts/rules";
    }

    private void addRuleFormModel(Model model, AlertRuleForm form) {
        List<Application> applications = accessService.getApplicationsWithPermission("ALERT_CONFIG");
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        List<MonitoringJob> jobs = applicationIds.isEmpty() ? List.of()
                : jobRepository.findByExecutionModeAndApplication_IdInOrderByNameAsc(
                        MonitoringJob.ExecutionMode.STANDARD, applicationIds);
        model.addAttribute("ruleForm", form);
        model.addAttribute("monitoringJobs", jobs);
        model.addAttribute("severities", AlertRule.Severity.values());
        model.addAttribute("sourcePolicies", AlertRule.ExecutionSourcePolicy.values());
        model.addAttribute("notificationPolicies", notificationPolicyRepository.findByEnabledTrueOrderByNameAsc());
    }

    private void assertRuleAccess(AlertRule rule, String permission) {
        if (rule.getMonitoringJob() == null) throw new AccessDeniedException("The alert rule has no monitoring job.");
        assertJobAccess(rule.getMonitoringJob(), permission);
    }

    private void assertJobAccess(MonitoringJob job, String permission) {
        if (job.getApplication() == null) throw new AccessDeniedException("The monitoring job has no application scope.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), permission);
    }

    private List<Long> applicationIds(String permission) {
        return accessService.getApplicationsWithPermission(permission).stream().map(Application::getId).toList();
    }

    private Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size == 5 || size == 10 || size == 25 ? size : 10;
        return PageRequest.of(safePage, safeSize);
    }

    private Long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) return "Please check the alert rule configuration.";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
