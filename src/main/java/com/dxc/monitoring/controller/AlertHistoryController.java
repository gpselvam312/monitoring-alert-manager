package com.dxc.monitoring.controller;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/alerts/history")
public class AlertHistoryController {
    private final AlertRepository alertRepository;
    private final DashboardAccessService accessService;
    private final EnvironmentRepository environmentRepository;

    public AlertHistoryController(AlertRepository alertRepository, DashboardAccessService accessService,
            EnvironmentRepository environmentRepository) {
        this.alertRepository = alertRepository;
        this.accessService = accessService;
        this.environmentRepository = environmentRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Model model) {
        List<Application> applications = accessService.getApplicationsWithPermission("ALERT_VIEW");
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        boolean invalidApplication = applicationId != null && !applicationIds.contains(applicationId);
        String safeSearch = search == null ? "" : search.trim();
        if (safeSearch.length() > 150) safeSearch = safeSearch.substring(0, 150);
        String repositorySearch = safeSearch;
        Long exactAlertId = null;
        Matcher alertKeyMatcher = Pattern.compile("(?i)^ALT-\\d{4}-(\\d{8,})$").matcher(safeSearch);
        if (alertKeyMatcher.matches()) {
            try {
                exactAlertId = Long.parseLong(alertKeyMatcher.group(1));
                repositorySearch = "";
            } catch (NumberFormatException ignored) {
                repositorySearch = safeSearch;
            }
        }

        Alert.AlertStatus statusFilter = parseStatus(status);
        AlertRule.Severity severityFilter = parseSeverity(severity);
        PageRequestSupport.PageSpec pageSpec = PageRequestSupport.create(page, size, sort, direction,
                "createdAt", Map.of(
                        "createdAt", "createdAt",
                        "lastOccurredAt", "lastOccurredAt",
                        "resolvedAt", "resolvedAt",
                        "severity", "severity",
                        "status", "status",
                        "title", "title"));
        Pageable pageable = pageSpec.pageable();
        int safeSize = pageSpec.pageSize();
        String sortProperty = pageSpec.sort();
        String safeDirection = pageSpec.direction();

        Page<Alert> alerts = applicationIds.isEmpty() || invalidApplication
                ? Page.empty(pageable)
                : alertRepository.searchHistory(applicationIds, applicationId, environmentId, exactAlertId,
                        statusFilter, severityFilter, repositorySearch, pageable);
        long total = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, exactAlertId,
                        null, severityFilter, repositorySearch);
        long active = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, exactAlertId,
                        Alert.AlertStatus.ACTIVE, severityFilter, repositorySearch);
        long resolved = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, exactAlertId,
                        Alert.AlertStatus.RESOLVED, severityFilter, repositorySearch);

        List<Environment> environments = applicationId == null || invalidApplication ? List.of()
                : environmentRepository.findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(applicationId);
        model.addAttribute("alerts", alerts);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("search", safeSearch);
        model.addAttribute("status", statusFilter == null ? "" : statusFilter.name());
        model.addAttribute("severity", severityFilter == null ? "" : severityFilter.name());
        model.addAttribute("applicationId", applicationId);
        model.addAttribute("environmentId", environmentId);
        model.addAttribute("applications", applications);
        model.addAttribute("environments", environments);
        model.addAttribute("sort", sortProperty);
        model.addAttribute("direction", safeDirection);
        model.addAttribute("totalAlerts", total);
        model.addAttribute("activeAlerts", active);
        model.addAttribute("resolvedAlerts", resolved);
        model.addAttribute("currentPage", "alert-history");
        return "alerts/history";
    }

    private Alert.AlertStatus parseStatus(String value) {
        try { return value == null || value.isBlank() ? null : Alert.AlertStatus.valueOf(value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private AlertRule.Severity parseSeverity(String value) {
        try { return value == null || value.isBlank() ? null : AlertRule.Severity.valueOf(value); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
