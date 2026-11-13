package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Controller
@RequestMapping("/alerts/history")
public class AlertHistoryController {
    private final AlertRepository alertRepository;
    private final DashboardAccessService accessService;

    public AlertHistoryController(AlertRepository alertRepository, DashboardAccessService accessService) {
        this.alertRepository = alertRepository;
        this.accessService = accessService;
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
            @RequestParam(defaultValue = "lastOccurredAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Model model) {
        List<Application> applications = accessService.getApplicationsWithPermission("ALERT_VIEW");
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        boolean invalidApplication = applicationId != null && !applicationIds.contains(applicationId);
        String safeSearch = search == null ? "" : search.trim();
        if (safeSearch.length() > 150) safeSearch = safeSearch.substring(0, 150);

        Alert.AlertStatus statusFilter = parseStatus(status);
        AlertRule.Severity severityFilter = parseSeverity(severity);
        int safeSize = size == 5 || size == 25 ? size : 10;
        int safePage = Math.max(0, page);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = switch (sort == null ? "" : sort) {
            case "createdAt" -> "createdAt";
            case "lastOccurredAt" -> "lastOccurredAt";
            case "resolvedAt" -> "resolvedAt";
            case "severity" -> "severity";
            case "status" -> "status";
            case "title" -> "title";
            default -> "lastOccurredAt";
        };
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(sortDirection, sortProperty).and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Alert> alerts = applicationIds.isEmpty() || invalidApplication
                ? Page.empty(pageable)
                : alertRepository.searchHistory(applicationIds, applicationId, environmentId,
                        statusFilter, severityFilter, safeSearch, pageable);
        long total = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId,
                        null, severityFilter, safeSearch);
        long active = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId,
                        Alert.AlertStatus.ACTIVE, severityFilter, safeSearch);
        long resolved = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId,
                        Alert.AlertStatus.RESOLVED, severityFilter, safeSearch);

        List<Environment> environments = applicationId == null || invalidApplication ? List.of()
                : accessService.getEnvironmentsForApplication(applicationId);
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
        model.addAttribute("direction", sortDirection.name().toLowerCase());
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
