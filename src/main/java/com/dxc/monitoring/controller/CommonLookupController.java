package com.dxc.monitoring.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dxc.monitoring.config.LookupProperties;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@RestController
@RequestMapping("/api/lookups")
@PreAuthorize("hasAuthority('ALERT_CONFIG')")
public class CommonLookupController {
    private static final String PERMISSION = "ALERT_CONFIG";

    private final MonitoringJobRepository jobs;
    private final EnvironmentRepository environments;
    private final DashboardAccessService accessService;
    private final LookupProperties properties;

    public CommonLookupController(MonitoringJobRepository jobs, EnvironmentRepository environments,
            DashboardAccessService accessService, LookupProperties properties) {
        this.jobs = jobs;
        this.environments = environments;
        this.accessService = accessService;
        this.properties = properties;
    }

    @GetMapping("/monitoring-jobs")
    public LookupPage<MonitoringJobRow> monitoringJobs(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "0") int batch,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        List<Long> applicationIds = accessService.getApplicationsWithPermission(PERMISSION)
                .stream().map(Application::getId).toList();
        int fetchSize = properties.getFetchSize();
        int safeBatch = Math.max(0, Math.min(batch, 1_000_000));
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safeBatch, fetchSize,
                Sort.by(sortDirection, sortProperty(sort)).and(Sort.by(Sort.Direction.ASC, "id")));

        if (applicationIds.isEmpty()) {
            return new LookupPage<>(List.of(), 0, safeBatch, false, fetchSize);
        }
        if (applicationId != null && !applicationIds.contains(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this application.");
        }
        if (environmentId != null) {
            Environment selectedEnvironment = environments.findById(environmentId)
                    .filter(Environment::isEnabled)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Choose an available environment."));
            Long environmentApplicationId = selectedEnvironment.getApplication() == null
                    ? null : selectedEnvironment.getApplication().getId();
            if (environmentApplicationId == null || !applicationIds.contains(environmentApplicationId)
                    || (applicationId != null && !applicationId.equals(environmentApplicationId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choose an environment within your authorized application scope.");
            }
        }

        String safeSearch = search == null ? "" : search.trim();
        Page<MonitoringJob> page = jobs.findAllForLookup(safeSearch, applicationIds, applicationId,
                environmentId, MonitoringJob.ExecutionMode.STANDARD, pageable);
        List<MonitoringJobRow> rows = page.getContent().stream().map(job -> {
            String applicationName = job.getApplication() == null ? "" : job.getApplication().getName();
            String environmentName = job.getEnvironment() == null ? "" : job.getEnvironment().getName();
            String displayName = job.getName() + " — " + applicationName
                    + (environmentName.isBlank() ? "" : " / " + environmentName);
            return new MonitoringJobRow(job.getId(), job.getName(), displayName, job.getDescription(),
                    job.getApplication() == null ? null : job.getApplication().getId(), applicationName,
                    job.getEnvironment() == null ? null : job.getEnvironment().getId(), environmentName,
                    job.getType() == null ? "" : job.getType().name(), job.isEnabled());
        }).toList();
        return new LookupPage<>(rows, page.getTotalElements(), safeBatch, page.hasNext(), fetchSize);
    }

    @GetMapping("/monitoring-jobs/filters")
    public Map<String, Object> monitoringJobFilters(@RequestParam(required = false) Long applicationId) {
        List<Application> accessible = accessService.getApplicationsWithPermission(PERMISSION);
        List<Long> applicationIds = accessible.stream().map(Application::getId).toList();
        if (applicationId != null && !applicationIds.contains(applicationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this application.");
        }
        List<LookupOption> applicationOptions = accessible.stream()
                .map(a -> new LookupOption(a.getId(), a.getName())).toList();
        List<Environment> allowedEnvironments = applicationIds.isEmpty() ? List.of()
                : environments.findByApplicationIdInAndEnabledTrueOrderByNameIgnoreCase(applicationIds);
        List<LookupOption> environmentOptions = allowedEnvironments.stream()
                .filter(e -> applicationId == null || applicationId.equals(e.getApplication().getId()))
                .map(e -> new LookupOption(e.getId(), e.getName())).toList();
        return Map.of("applications", applicationOptions, "environments", environmentOptions);
    }

    private String sortProperty(String sort) {
        return switch (sort == null ? "" : sort.toLowerCase()) {
            case "application" -> "application.name";
            case "environment" -> "environment.name";
            default -> "name";
        };
    }

    public record LookupOption(Long id, String name) {}
    public record MonitoringJobRow(Long id, String name, String displayName, String description, Long applicationId,
            String applicationName, Long environmentId, String environmentName, String type, boolean enabled) {}
    public record LookupPage<T>(List<T> items, long totalElements, int batch, boolean hasNext, int fetchSize) {}
}
