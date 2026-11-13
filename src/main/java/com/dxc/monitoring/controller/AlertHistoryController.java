package com.dxc.monitoring.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.dxc.monitoring.config.LookupProperties;
import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.service.SystemSettingService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/alerts/history")
public class AlertHistoryController {
    private static final Pattern ALERT_KEY_PATTERN = Pattern.compile("(?i)^ALT-\\d{4}-(\\d{8,})$");
    private final AlertRepository alertRepository;
    private final DashboardAccessService accessService;
    private final EnvironmentRepository environmentRepository;
    private final LookupProperties lookupProperties;
    private final SystemSettingService systemSettingService;

    public AlertHistoryController(AlertRepository alertRepository, DashboardAccessService accessService,
            EnvironmentRepository environmentRepository, LookupProperties lookupProperties,
            SystemSettingService systemSettingService) {
        this.alertRepository = alertRepository;
        this.accessService = accessService;
        this.environmentRepository = environmentRepository;
        this.lookupProperties = lookupProperties;
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String history(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "10") int size,
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
        validateEnvironment(environmentId, applicationId, applicationIds);

        String safeSearch = normalizeSearch(search);
        SearchTerm term = searchTerm(safeSearch);
        Alert.AlertStatus statusFilter = parseStatus(status);
        AlertRule.Severity severityFilter = parseSeverity(severity);
        PageRequestSupport.PageSpec pageSpec = PageRequestSupport.create(0, size, sort, direction,
                "createdAt", allowedSorts());
        long total = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        null, severityFilter, term.repositorySearch());
        long active = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        Alert.AlertStatus.ACTIVE, severityFilter, term.repositorySearch());
        long resolved = applicationIds.isEmpty() || invalidApplication ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        Alert.AlertStatus.RESOLVED, severityFilter, term.repositorySearch());

        List<Environment> environments = applicationId == null || invalidApplication ? List.of()
                : environmentRepository.findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(applicationId);
        model.addAttribute("pageSize", pageSpec.pageSize());
        model.addAttribute("search", safeSearch);
        model.addAttribute("status", statusFilter == null ? "" : statusFilter.name());
        model.addAttribute("severity", severityFilter == null ? "" : severityFilter.name());
        model.addAttribute("applicationId", applicationId);
        model.addAttribute("environmentId", environmentId);
        model.addAttribute("applications", applications);
        model.addAttribute("environments", environments);
        model.addAttribute("sort", pageSpec.sort());
        model.addAttribute("direction", pageSpec.direction());
        model.addAttribute("totalAlerts", total);
        model.addAttribute("activeAlerts", active);
        model.addAttribute("resolvedAlerts", resolved);
        model.addAttribute("currentPage", "alert-history");
        return "alerts/history";
    }

    @GetMapping("/batch")
    @ResponseBody
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public HistoryBatch historyBatch(@RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "0") int batch,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        List<Long> applicationIds = accessService.getApplicationsWithPermission("ALERT_VIEW")
                .stream().map(Application::getId).toList();
        int fetchSize = lookupProperties.getFetchSize();
        int safeBatch = Math.max(0, Math.min(batch, 1_000_000));
        Pageable pageable = PageRequest.of(safeBatch, fetchSize,
                Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        sortProperty(sort)).and(Sort.by(Sort.Direction.DESC, "id")));
        if (applicationIds.isEmpty()) {
            return new HistoryBatch(List.of(), 0, safeBatch, false, fetchSize);
        }
        if (applicationId != null && !applicationIds.contains(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this application.");
        }
        validateEnvironment(environmentId, applicationId, applicationIds);

        SearchTerm term = searchTerm(normalizeSearch(search));
        Page<Alert> page = alertRepository.searchHistory(applicationIds, applicationId, environmentId,
                term.exactAlertId(), parseStatus(status), parseSeverity(severity), term.repositorySearch(), pageable);
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
                systemSettingService.getDateTimeFormat(), Locale.ENGLISH);
        List<HistoryRow> rows = page.getContent().stream()
                .map(alert -> toHistoryRow(alert, dateTimeFormatter)).toList();
        return new HistoryBatch(rows, page.getTotalElements(), safeBatch, page.hasNext(), fetchSize);
    }

    private void validateEnvironment(Long environmentId, Long applicationId, List<Long> applicationIds) {
        if (environmentId == null) return;
        Environment environment = environmentRepository.findById(environmentId)
                .filter(Environment::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choose an available environment."));
        Long environmentApplicationId = environment.getApplication() == null
                ? null : environment.getApplication().getId();
        if (environmentApplicationId == null || !applicationIds.contains(environmentApplicationId)
                || (applicationId != null && !applicationId.equals(environmentApplicationId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose an environment within your authorized application scope.");
        }
    }

    private HistoryRow toHistoryRow(Alert alert, DateTimeFormatter dateTimeFormatter) {
        return new HistoryRow(alert.getId(), alert.getAlertKey(), alert.getTitle(),
                alert.getMonitoringJob().getName(), alert.getSeverity().name(),
                alert.getMonitoringJob().getApplication().getName(),
                alert.getMonitoringJob().getEnvironment() == null ? "—" : alert.getMonitoringJob().getEnvironment().getName(),
                alert.getStatus().name(), alert.getOccurrenceCount(), format(alert.getCreatedAt(), dateTimeFormatter),
                format(alert.getLastOccurredAt(), dateTimeFormatter), format(alert.getResolvedAt(), dateTimeFormatter));
    }

    private String format(java.time.OffsetDateTime value, DateTimeFormatter dateTimeFormatter) {
        return value == null ? "—" : dateTimeFormatter.format(value);
    }

    private Map<String, String> allowedSorts() {
        return Map.of("alertKey", "id", "occurrenceCount", "occurrenceCount",
                "createdAt", "createdAt", "lastOccurredAt", "lastOccurredAt",
                "resolvedAt", "resolvedAt", "severity", "severity", "status", "status", "title", "title");
    }

    private String sortProperty(String sort) {
        return allowedSorts().getOrDefault(sort, "createdAt");
    }

    private String normalizeSearch(String search) {
        String value = search == null ? "" : search.trim();
        return value.length() > 150 ? value.substring(0, 150) : value;
    }

    private SearchTerm searchTerm(String search) {
        Matcher matcher = ALERT_KEY_PATTERN.matcher(search);
        if (matcher.matches()) {
            try {
                return new SearchTerm(Long.parseLong(matcher.group(1)), "");
            } catch (NumberFormatException ignored) {
                // Fall back to normal text search for an out-of-range key.
            }
        }
        return new SearchTerm(null, search);
    }

    private Alert.AlertStatus parseStatus(String value) {
        try { return value == null || value.isBlank() ? null : Alert.AlertStatus.valueOf(value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private AlertRule.Severity parseSeverity(String value) {
        try { return value == null || value.isBlank() ? null : AlertRule.Severity.valueOf(value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private record SearchTerm(Long exactAlertId, String repositorySearch) {}
    public record HistoryRow(Long id, String alertKey, String title, String jobName, String severity,
            String applicationName, String environmentName, String status, Long occurrenceCount,
            String createdAt, String lastOccurredAt, String resolvedAt) {}
    public record HistoryBatch(List<HistoryRow> items, long totalElements, int batch,
            boolean hasNext, int fetchSize) {}
}
