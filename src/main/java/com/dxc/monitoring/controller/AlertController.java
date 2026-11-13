package com.dxc.monitoring.controller;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertCommentRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.AlertRuleEvaluationRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.repository.UserAlertStateRepository;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.AlertRuleManagementService;
import com.dxc.monitoring.service.AlertRuleConfigurationService;
import com.dxc.monitoring.service.AlertRuleNotificationRouteService;
import com.dxc.monitoring.service.AlertRuleFieldDiscoveryService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.AlertRuleForm;
import com.dxc.monitoring.web.PageRequestSupport;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/alerts")
public class AlertController {
    private static final Logger log = LoggerFactory.getLogger(AlertController.class);
    private final AlertRepository alertRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertCommentRepository commentRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertRuleEvaluationRepository ruleEvaluationRepository;
    private final MonitoringJobRepository jobRepository;
    private final AlertRuleManagementService ruleManagementService;
    private final AlertRuleConfigurationService ruleConfigurationService;
    private final DashboardAccessService accessService;
    private final UserAlertStateRepository userAlertStateRepository;
    private final NotificationPolicyRepository notificationPolicyRepository;
    private final AlertRuleFieldDiscoveryService fieldDiscoveryService;
    private final AlertRuleNotificationRouteService notificationRouteService;

    public AlertController(AlertRepository alertRepository, AlertHistoryRepository historyRepository,
            AlertCommentRepository commentRepository, AlertRuleRepository ruleRepository, AlertRuleEvaluationRepository ruleEvaluationRepository,
            MonitoringJobRepository jobRepository,
            AlertRuleManagementService ruleManagementService, AlertRuleConfigurationService ruleConfigurationService,
            DashboardAccessService accessService,
            UserAlertStateRepository userAlertStateRepository,
            NotificationPolicyRepository notificationPolicyRepository,
            AlertRuleFieldDiscoveryService fieldDiscoveryService,
            AlertRuleNotificationRouteService notificationRouteService) {
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.ruleRepository = ruleRepository;
        this.ruleEvaluationRepository = ruleEvaluationRepository;
        this.jobRepository = jobRepository;
        this.ruleManagementService = ruleManagementService;
        this.ruleConfigurationService = ruleConfigurationService;
        this.accessService = accessService;
        this.userAlertStateRepository = userAlertStateRepository;
        this.notificationPolicyRepository = notificationPolicyRepository;
        this.fieldDiscoveryService = fieldDiscoveryService;
        this.notificationRouteService = notificationRouteService;
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String alertDetails(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "active") String from,
            Authentication authentication, Model model) {
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
        model.addAttribute("comments", commentRepository.findByAlertIdOrderByCreatedAtDescIdDesc(id));
        model.addAttribute("pageSize", pageable.getPageSize());
        String detailSource = "history".equalsIgnoreCase(from) ? "history" : "active";
        model.addAttribute("detailSource", detailSource);
        model.addAttribute("currentPage", "history".equals(detailSource) ? "alert-history" : "active-alerts");
        return "alerts/detail";
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String alertRules(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, Model model) {
        addRuleListModel(model, page, size, search, sort, direction);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/rules";
    }

    @GetMapping("/rules/table")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String alertRulesTable(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, Model model) {
        addRuleListModel(model, page, size, search, sort, direction);
        return "alerts/rules :: rulesTable";
    }

    @GetMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String viewRule(@PathVariable Long id, Model model) {
        AlertRule rule = ruleRepository.findByIdWithJob(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule was not found."));
        assertRuleAccess(rule, "ALERT_CONFIG");
        AlertRuleForm form = ruleManagementService.toForm(rule);
        form.setNotificationRoutesJson(notificationRouteService.routesAsJson(rule.getId()));
        addRuleFormModel(model, form);
        model.addAttribute("rule", rule);
        model.addAttribute("viewMode", true);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/alert_rule_form";
    }

    @PostMapping("/rules/{id}/delete")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String deleteRule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AlertRule rule = ruleRepository.findByIdWithJob(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule was not found."));
        assertRuleAccess(rule, "ALERT_CONFIG");
        if (alertRepository.existsByAlertRuleId(id) || ruleEvaluationRepository.existsByAlertRuleId(id)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This rule has alert or evaluation history and cannot be deleted without losing audit history. Disable it instead.");
            return "redirect:/alerts/rules";
        }
        ruleRepository.delete(rule);
        redirectAttributes.addFlashAttribute("successMessage", "Alert rule deleted successfully.");
        return "redirect:/alerts/rules";
    }

    private void addRuleListModel(Model model, int page, int size, String search, String sort, String direction) {
        String safeSearch = search == null ? "" : search.trim();
        PageRequestSupport.PageSpec pageSpec = PageRequestSupport.create(page, size, sort, direction,
                "updatedAt", Map.of(
                        "name", "name",
                        "monitoringJob.name", "monitoringJob.name",
                        "monitoringJob.application.name", "monitoringJob.application.name",
                        "monitoringJob.environment.name", "monitoringJob.environment.name",
                        "severity", "severity",
                        "executionSourcePolicy", "executionSourcePolicy",
                        "enabled", "enabled"));
        int safeSize = pageSpec.pageSize();
        String sortProperty = pageSpec.sort();
        Pageable pageable = pageSpec.pageable();
        List<Long> allowedApplicationIds = applicationIds("ALERT_CONFIG");
        Page<AlertRule> rules = allowedApplicationIds.isEmpty() ? Page.empty(pageable)
                : ruleRepository.searchForManagement(allowedApplicationIds, safeSearch, pageable);
        model.addAttribute("rules", rules);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("search", safeSearch);
        model.addAttribute("sort", sortProperty);
        model.addAttribute("direction", pageSpec.direction());
    }

    @GetMapping("/rules/new")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String newRule(Model model) {
        AlertRuleForm form = new AlertRuleForm();
        addRuleFormModel(model, form);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/alert_rule_form";
    }

    @GetMapping("/rules/{id}/edit")
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public String editRule(@PathVariable Long id, Model model) {
        AlertRule rule = ruleRepository.findByIdWithJob(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule was not found."));
        assertRuleAccess(rule, "ALERT_CONFIG");
        AlertRuleForm form = ruleManagementService.toForm(rule);
        form.setNotificationRoutesJson(notificationRouteService.routesAsJson(rule.getId()));
        addRuleFormModel(model, form);
        model.addAttribute("currentPage", "alert-rules");
        return "alerts/alert_rule_form";
    }

    @PostMapping("/rules/discovery/sample")
    @ResponseBody
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public ResponseEntity<?> discoverSampleFields(@RequestBody(required = false) String sample) {
        try {
            return ResponseEntity.ok(fieldDiscoveryService.discoverSample(sample));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(exception.getMessage())));
        }
    }

    @GetMapping("/rules/discovery/{jobId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('ALERT_CONFIG')")
    public ResponseEntity<?> discoverRuleFields(@PathVariable Long jobId) {
        MonitoringJob job = jobRepository.findByIdForDetails(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitoring job was not found."));
        assertJobAccess(job, "ALERT_CONFIG");
        if (job.getExecutionMode() != MonitoringJob.ExecutionMode.STANDARD) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Field discovery is available only for standard monitoring jobs."));
        }
        try {
            return ResponseEntity.ok(fieldDiscoveryService.discover(job));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(exception.getMessage())));
        }
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
            ruleConfigurationService.save(form, userId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Alert rule saved successfully.");
            return "redirect:/alerts/rules";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            log.error("Unable to save alert rule id={} with its notification routes", form.getId(), exception);
            addRuleFormModel(model, form);
            model.addAttribute("errorMessage", exception instanceof DataIntegrityViolationException
                    ? "Unable to save the alert rule or its notification routes because of a data constraint. Review the configured values and try again."
                    : safeMessage(exception.getMessage()));
            model.addAttribute("currentPage", "alert-rules");
            return "alerts/alert_rule_form";
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
        boolean hasMonitoringJobs = !applicationIds.isEmpty()
                && jobRepository.existsByExecutionModeAndApplication_IdIn(
                        MonitoringJob.ExecutionMode.STANDARD, applicationIds);
        MonitoringJob selectedJob = form.getMonitoringJobId() == null ? null
                : jobRepository.findByIdForDetails(form.getMonitoringJobId())
                        .filter(job -> job.getExecutionMode() == MonitoringJob.ExecutionMode.STANDARD)
                        .filter(job -> job.getApplication() != null && applicationIds.contains(job.getApplication().getId()))
                        .orElse(null);
        String selectedMonitoringJobLabel = selectedJob == null ? ""
                : selectedJob.getName() + " — " + selectedJob.getApplication().getName()
                        + (selectedJob.getEnvironment() != null ? " / " + selectedJob.getEnvironment().getName() : "");
        model.addAttribute("ruleForm", form);
        model.addAttribute("viewMode", false);
        model.addAttribute("hasMonitoringJobs", hasMonitoringJobs);
        model.addAttribute("selectedMonitoringJobLabel", selectedMonitoringJobLabel);
        model.addAttribute("severities", AlertRule.Severity.values());
        model.addAttribute("sourcePolicies", AlertRule.ExecutionSourcePolicy.values());
        model.addAttribute("notificationChannels", notificationRouteService.enabledChannels());
        model.addAttribute("emailSenderAddresses", notificationRouteService.emailSenderAddresses());
        model.addAttribute("notificationRecipients", notificationRouteService.enabledRecipients());
        model.addAttribute("notificationGroups", notificationRouteService.enabledGroups());
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
