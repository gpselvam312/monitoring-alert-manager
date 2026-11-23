package com.dxc.monitoring.controller;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.dxc.monitoring.repository.AlertRuleNotificationRouteRepository;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.service.SystemSettingService;
import com.dxc.monitoring.service.ReferenceDataCacheService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/alerts/history")
public class AlertHistoryController {
    private static final Pattern ALERT_KEY_PATTERN = Pattern.compile("(?i)^ALT-\\d{4}-(\\d{8,})$");
    private final AlertRepository alertRepository;
    private final DashboardAccessService accessService;
    private final ReferenceDataCacheService referenceDataCache;
    private final LookupProperties lookupProperties;
    private final SystemSettingService systemSettingService;
    private final AlertRuleNotificationRouteRepository notificationRouteRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final PageRequestSupport pageRequestSupport;

    public AlertHistoryController(AlertRepository alertRepository, DashboardAccessService accessService,
            ReferenceDataCacheService referenceDataCache, LookupProperties lookupProperties,
            SystemSettingService systemSettingService,
            AlertRuleNotificationRouteRepository notificationRouteRepository,
            NotificationDeliveryRepository notificationDeliveryRepository, PageRequestSupport pageRequestSupport) {
        this.alertRepository = alertRepository;
        this.accessService = accessService;
        this.referenceDataCache = referenceDataCache;
        this.lookupProperties = lookupProperties;
        this.systemSettingService = systemSettingService;
        this.notificationRouteRepository = notificationRouteRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String history(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "triggeredAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Model model) {
        List<Application> applications = accessService.getApplicationsWithPermission("ALERT_VIEW");
        List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        if (applicationId != null && !applicationIds.contains(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this application.");
        }
        validateEnvironment(environmentId, applicationId, applicationIds);

        String safeSearch = normalizeSearch(search);
        SearchTerm term = searchTerm(safeSearch);
        Alert.AlertStatus statusFilter = parseStatus(status);
        AlertRule.Severity severityFilter = parseSeverity(severity);
        PageRequestSupport.PageSpec pageSpec = pageRequestSupport.create(0, size, sort, direction,
                "triggeredAt", allowedSorts());
        long total = applicationIds.isEmpty() ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        null, severityFilter, term.repositorySearch());
        long active = applicationIds.isEmpty() ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        Alert.AlertStatus.ACTIVE, severityFilter, term.repositorySearch());
        long resolved = applicationIds.isEmpty() ? 0
                : alertRepository.countHistory(applicationIds, applicationId, environmentId, term.exactAlertId(),
                        Alert.AlertStatus.RESOLVED, severityFilter, term.repositorySearch());

        List<Environment> environments = applicationId == null ? List.of()
                : referenceDataCache.getEnabledEnvironments(applicationId);
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
            @RequestParam(defaultValue = "triggeredAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        List<Long> applicationIds = accessService.getApplicationsWithPermission("ALERT_VIEW")
                .stream().map(Application::getId).toList();
        int fetchSize = lookupProperties.getFetchSize();
        int safeBatch = Math.max(0, Math.min(batch, 1_000_000));
        PageRequestSupport.PageSpec batchPage = pageRequestSupport.createWithFixedSize(
                safeBatch, fetchSize, sort, direction, "triggeredAt", allowedSorts());
        Pageable pageable = batchPage.pageable();
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
        List<Alert> alerts = page.getContent();
        List<Long> alertIds = alerts.stream().map(Alert::getId).toList();
        List<Long> ruleIds = alerts.stream().map(alert -> alert.getAlertRule().getId()).distinct().toList();

        Map<Long, Set<String>> configuredChannelsByRule = new HashMap<>();
        if (!ruleIds.isEmpty()) {
            for (Object[] route : notificationRouteRepository.findConfiguredChannelTypesByAlertRuleIds(ruleIds)) {
                Long ruleId = ((Number) route[0]).longValue();
                configuredChannelsByRule.computeIfAbsent(ruleId, ignored -> new java.util.HashSet<>())
                        .add(String.valueOf(route[1]));
            }
        }

        Map<Long, Map<String, NotificationDeliveryRepository.ChannelDeliverySummary>> deliveriesByAlert =
                new HashMap<>();
        if (!alertIds.isEmpty()) {
            for (NotificationDeliveryRepository.ChannelDeliverySummary summary
                    : notificationDeliveryRepository.summarizeLatestByAlertIds(alertIds)) {
                deliveriesByAlert.computeIfAbsent(summary.getAlertId(), ignored -> new HashMap<>())
                        .put(summary.getChannelType(), summary);
            }
        }

        List<HistoryRow> rows = alerts.stream()
                .map(alert -> toHistoryRow(alert, dateTimeFormatter,
                        configuredChannelsByRule.getOrDefault(alert.getAlertRule().getId(), Set.of()),
                        deliveriesByAlert.getOrDefault(alert.getId(), Map.of())))
                .toList();
        return new HistoryBatch(rows, page.getTotalElements(), safeBatch, page.hasNext(), fetchSize);
    }

    private void validateEnvironment(Long environmentId, Long applicationId, List<Long> applicationIds) {
        if (environmentId == null) return;
        List<Environment> availableEnvironments = applicationId == null
                ? applicationIds.stream()
                        .flatMap(id -> referenceDataCache.getEnabledEnvironments(id).stream()).toList()
                : referenceDataCache.getEnabledEnvironments(applicationId);
        Environment environment = availableEnvironments.stream()
                .filter(candidate -> candidate.getId().equals(environmentId))
                .findFirst()
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

    private HistoryRow toHistoryRow(Alert alert, DateTimeFormatter dateTimeFormatter,
            Set<String> configuredChannels,
            Map<String, NotificationDeliveryRepository.ChannelDeliverySummary> deliveries) {
        Map<String, ChannelStatus> channels = new LinkedHashMap<>();
        for (String channelType : List.of("EMAIL", "TEAMS", "WHATSAPP")) {
            NotificationDeliveryRepository.ChannelDeliverySummary delivery = deliveries.get(channelType);
            boolean configured = configuredChannels.contains(channelType) || delivery != null;
            ChannelStatus channelStatus = !configured
                    ? new ChannelStatus(false, "NOT_CONFIGURED", 0, 0, 0, 0)
                    : delivery == null
                            ? new ChannelStatus(true, "NOT_SENT", 0, 0, 0, 0)
                            : new ChannelStatus(true, delivery.getDeliveryStatus(),
                                    safeCount(delivery.getSentCount()), safeCount(delivery.getFailedCount()),
                                    safeCount(delivery.getPendingCount()), safeCount(delivery.getSkippedCount()));
            channels.put(channelType, channelStatus);
        }
        String environmentName = alert.getMonitoringJob().getEnvironment() == null
                ? "—" : alert.getMonitoringJob().getEnvironment().getName();
        return new HistoryRow(alert.getId(), alert.getAlertKey(), alert.getTitle(),
                alert.getAlertRule().getName(), alert.getMonitoringJob().getName(),
                alert.getSeverity().name(), alert.getMonitoringJob().getApplication().getName(),
                environmentName, alert.getStatus().name(), alert.getOccurrenceCount(),
                format(alert.getFirstOccurredAt(), dateTimeFormatter), channels);
    }

    private long safeCount(Long value) {
        return value == null ? 0 : value;
    }

    private String format(java.time.OffsetDateTime value, DateTimeFormatter dateTimeFormatter) {
        return value == null ? "—" : dateTimeFormatter.format(value);
    }

    private Map<String, String> allowedSorts() {
        return Map.of("alertKey", "id", "occurrenceCount", "occurrenceCount",
                "triggeredAt", "firstOccurredAt", "createdAt", "createdAt", "lastOccurredAt", "lastOccurredAt",
                "resolvedAt", "resolvedAt", "severity", "severity", "status", "status", "title", "title");
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
        if (value == null || value.isBlank()) return null;
        try {
            return Alert.AlertStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid alert status.");
        }
    }

    private AlertRule.Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AlertRule.Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid alert severity.");
        }
    }

    private record SearchTerm(Long exactAlertId, String repositorySearch) {}
    public record ChannelStatus(boolean configured, String status, long sentCount,
            long failedCount, long pendingCount, long skippedCount) {}
    public record HistoryRow(Long id, String alertKey, String title, String ruleName, String jobName,
            String severity, String applicationName, String environmentName, String status,
            Long occurrenceCount, String triggeredAt, Map<String, ChannelStatus> channels) {}
    public record HistoryBatch(List<HistoryRow> items, long totalElements, int batch,
            boolean hasNext, int fetchSize) {}
}
