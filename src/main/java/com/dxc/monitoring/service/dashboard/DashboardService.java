package com.dxc.monitoring.service.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.DashboardWidgetRepository;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class DashboardService
{
    private final DashboardTabRepository dashboardTabRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final MonitoringExecutionRepository monitoringExecutionRepository;
    private final MonitoringResultRepository monitoringResultRepository;
    private final MonitoringJobRepository monitoringJobRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(DashboardTabRepository dashboardTabRepository,
            DashboardWidgetRepository dashboardWidgetRepository,
            MonitoringExecutionRepository monitoringExecutionRepository,
            MonitoringResultRepository monitoringResultRepository,
            MonitoringJobRepository monitoringJobRepository,
            ObjectMapper objectMapper)
    {
        this.dashboardTabRepository = dashboardTabRepository;
        this.dashboardWidgetRepository = dashboardWidgetRepository;
        this.monitoringExecutionRepository = monitoringExecutionRepository;
        this.monitoringResultRepository = monitoringResultRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DashboardTabResponse> getDashboard()
    {
        List<DashboardTab> tabs = dashboardTabRepository.findAllByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(tab -> tab.getEnvironment() != null)
                .toList();
        List<DashboardWidget> widgets =
            dashboardWidgetRepository.findAllByEnabledTrueOrderByTabSortOrderAscSortOrderAsc();

        // Keep legacy result-backed widgets working, but resolve them to the owning job
        // so the dashboard always displays the latest execution rather than a frozen result.
        List<Long> resultIds = widgets.stream()
                .filter(widget -> "MONITORING_RESULT".equals(widget.getDataSourceType()))
                .map(DashboardWidget::getDataSourceId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, MonitoringResult> sourceResults = new HashMap<>();
        if (!resultIds.isEmpty())
        {
            monitoringResultRepository.findAllById(resultIds)
                    .forEach(result -> sourceResults.put(result.getId(), result));
        }

        List<Long> configuredJobIds = new ArrayList<>();
        widgets.stream()
                .filter(widget -> "MONITORING_JOB".equals(widget.getDataSourceType()))
                .map(DashboardWidget::getDataSourceId)
                .filter(id -> id != null)
                .forEach(configuredJobIds::add);
        sourceResults.values().stream()
                .filter(result -> result.getExecution() != null
                        && result.getExecution().getMonitoringJob() != null)
                .map(result -> result.getExecution().getMonitoringJob().getId())
                .filter(id -> id != null)
                .forEach(configuredJobIds::add);
        List<Long> jobIds = configuredJobIds.stream().distinct().toList();

        Map<Long, MonitoringExecution> latestExecutions = new HashMap<>();
        Map<Long, MonitoringJob> jobs = new HashMap<>();

        if (!jobIds.isEmpty())
        {
            monitoringJobRepository.findAllById(jobIds)
                    .forEach(job -> jobs.put(job.getId(), job));

            monitoringExecutionRepository.findLatestByMonitoringJobIds(jobIds)
                    .forEach(execution -> latestExecutions.put(execution.getMonitoringJob().getId(), execution));
        }

        Map<Long, MonitoringResult> latestResults = new HashMap<>();
        List<Long> executionIds = latestExecutions.values().stream().map(MonitoringExecution::getId).toList();

        if (!executionIds.isEmpty())
        {
            // Repository ordering puts the newest result for each execution first.
            monitoringResultRepository.findByExecutionIds(executionIds).forEach(result ->
                    latestResults.putIfAbsent(result.getExecution().getId(), result));
        }

        Map<Long, DashboardTabResponse> tabResponses = new HashMap<>();

        for (DashboardTab tab : tabs)
        {
            DashboardTabResponse response = new DashboardTabResponse();
            response.setId(tab.getId());
            response.setName(tab.getName());
            response.setEnvironmentId(tab.getEnvironment().getId());
            response.setEnvironmentName(tab.getEnvironment().getName());
            response.setSortOrder(tab.getSortOrder());
            tabResponses.put(tab.getId(), response);
        }

        for (DashboardWidget widget : widgets)
        {
            DashboardTabResponse tabResponse = tabResponses.get(widget.getTab().getId());

            if (tabResponse == null)
            {
                continue;
            }

            MonitoringExecution execution;
            MonitoringResult result;
            MonitoringJob job;

            if ("MONITORING_RESULT".equals(widget.getDataSourceType()))
            {
                MonitoringResult configuredResult =
                        widget.getDataSourceId() == null ? null : sourceResults.get(widget.getDataSourceId());
                Long sourceJobId = configuredResult == null || configuredResult.getExecution() == null
                        || configuredResult.getExecution().getMonitoringJob() == null
                                ? null : configuredResult.getExecution().getMonitoringJob().getId();
                job = sourceJobId == null ? null : jobs.get(sourceJobId);
                execution = sourceJobId == null ? null : latestExecutions.get(sourceJobId);
                result = execution == null ? null : latestResults.get(execution.getId());
            }
            else if ("MONITORING_JOB".equals(widget.getDataSourceType()))
            {
                Long sourceJobId = widget.getDataSourceId();
                job = sourceJobId == null ? null : jobs.get(sourceJobId);
                execution = sourceJobId == null ? null : latestExecutions.get(sourceJobId);
                result = execution == null ? null : latestResults.get(execution.getId());
            }
            else
            {
                execution = null;
                result = null;
                job = null;
            }

            tabResponse.getWidgets().add(createWidgetResponse(widget, job, execution, result));
        }

        List<DashboardTabResponse> response = new ArrayList<>();
        for (DashboardTab tab : tabs)
        {
            DashboardTabResponse tabResponse = tabResponses.get(tab.getId());
            if (tabResponse != null)
            {
                response.add(tabResponse);
            }
        }

        return response;
    }

    private DashboardWidgetResponse createWidgetResponse(DashboardWidget widget,
            MonitoringJob job, MonitoringExecution execution, MonitoringResult monitoringResult)
    {
        boolean environmentMismatch = job != null && !isEnvironmentCompatible(widget, job);
        if (environmentMismatch)
        {
            execution = null;
            monitoringResult = null;
        }

        DashboardWidgetDefinition definition = new DashboardWidgetDefinition();
        definition.setId(widget.getId());
        definition.setName(widget.getName());
        definition.setDescription(widget.getDescription());
        definition.setTabId(widget.getTab().getId());
        definition.setWidgetType(widget.getWidgetType());
        definition.setChartType(widget.getChartType());
        definition.setIcon(widget.getIcon());
        definition.setSize(widget.getSize());
        definition.setSortOrder(widget.getSortOrder());
        definition.setEnabled(widget.getEnabled());
        definition.setAutoRefresh(widget.getAutoRefresh());
        definition.setRefreshInterval(widget.getRefreshInterval());
        definition.setRefreshIntervalUnit(widget.getRefreshIntervalUnit());
        definition.setDetailsEnabled(widget.getDetailsEnabled());
        definition.setDataSourceType(widget.getDataSourceType());
        definition.setDataSourceId(widget.getDataSourceId());
        definition.setFieldConfigJson(widget.getFieldConfigJson());
        definition.setJobRunning(execution != null
                && execution.getStatus() == MonitoringExecution.ExecutionStatus.RUNNING);
        definition.setRunNowEnabled(canRunNow(widget, job, execution));

        WidgetResult result = createResult(execution, monitoringResult);

        if (environmentMismatch)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("Configured monitoring job belongs to a different environment.");
        }
        else if (("MONITORING_JOB".equals(widget.getDataSourceType())
                || "MONITORING_RESULT".equals(widget.getDataSourceType()))
                && widget.getDataSourceId() == null)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("Dashboard data source is not configured.");
        }
        else if ("MONITORING_JOB".equals(widget.getDataSourceType()) && job == null)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("Configured monitoring job was not found.");
        }
        else if ("MONITORING_RESULT".equals(widget.getDataSourceType()) && monitoringResult == null)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("Configured monitoring result was not found.");
        }

        DashboardWidgetResponse response = new DashboardWidgetResponse();
        response.setWidget(definition);
        response.setResult(result);
        return response;
    }

    private WidgetResult createResult(MonitoringExecution execution, MonitoringResult monitoringResult)
    {
        WidgetResult result = new WidgetResult();

        if (execution == null)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("No execution data");
            return result;
        }

        result.setStatus(mapExecutionStatus(execution.getStatus(), monitoringResult));
        result.setMessage(monitoringResult != null ? monitoringResult.getMessage() : execution.getErrorMessage());
        if (execution.getCompletedAt() != null)
        {
            result.setLastUpdated(execution.getCompletedAt().toLocalDateTime());
        }
        else if (execution.getStartedAt() != null)
        {
            result.setLastUpdated(execution.getStartedAt().toLocalDateTime());
        }

        if (monitoringResult != null)
        {
            result.setValue(monitoringResult.getValue());
            deserializeResultData(monitoringResult.getResultData(), result);
        }

        return result;
    }

    private boolean canRunNow(DashboardWidget widget, MonitoringJob job, MonitoringExecution execution)
    {
        if (!"MONITORING_JOB".equals(widget.getDataSourceType()) || job == null || !job.isManualRunEnabled()
                || !job.isEnabled() || !isEnvironmentCompatible(widget, job))
        {
            return false;
        }

        boolean hasPermission = SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(authority -> "MONITORING_RUN".equals(authority.getAuthority()));

        if (!hasPermission)
        {
            return false;
        }

        return job.isAllowConcurrentExecution()
                || execution == null
                || execution.getStatus() != MonitoringExecution.ExecutionStatus.RUNNING;
    }

    private boolean isEnvironmentCompatible(DashboardWidget widget, MonitoringJob job)
    {
        return widget.getTab().getEnvironment() != null
                && job.getEnvironment() != null
                && widget.getTab().getEnvironment().getId().equals(job.getEnvironment().getId());
    }

    private WidgetStatus mapExecutionStatus(MonitoringExecution.ExecutionStatus executionStatus,
            MonitoringResult monitoringResult)
    {
        if (monitoringResult != null && monitoringResult.getStatus() != null)
        {
            return switch (monitoringResult.getStatus())
            {
                case OK -> WidgetStatus.GREEN;
                case WARNING -> WidgetStatus.YELLOW;
                case FAILED -> WidgetStatus.RED;
            };
        }

        if (executionStatus == null)
        {
            return WidgetStatus.GRAY;
        }

        return switch (executionStatus)
        {
            case SUCCESS -> WidgetStatus.GREEN;
            case RUNNING -> WidgetStatus.YELLOW;
            case FAILED, TIMEOUT, ERROR -> WidgetStatus.RED;
        };
    }

    private void deserializeResultData(String resultData, WidgetResult result)
    {
        if (resultData == null || resultData.isBlank())
        {
            return;
        }

        try
        {
            Map<String, Object> root =
                    objectMapper.readValue(resultData, new TypeReference<Map<String, Object>>() {});
            result.setPayload(root);

            Object envelopeMessage = root.get("message");
            if ((result.getMessage() == null || result.getMessage().isBlank()) && envelopeMessage != null)
            {
                result.setMessage(String.valueOf(envelopeMessage));
            }

            Object rawStatus = root.get("status");
            if (rawStatus != null)
            {
                switch (String.valueOf(rawStatus).trim().toUpperCase())
                {
                    case "SUCCESS", "OK", "GREEN", "HEALTHY" -> result.setStatus(WidgetStatus.GREEN);
                    case "WARNING", "YELLOW" -> result.setStatus(WidgetStatus.YELLOW);
                    case "FAILURE", "FAILED", "RED", "CRITICAL" -> result.setStatus(WidgetStatus.RED);
                    case "UNKNOWN", "GRAY" -> result.setStatus(WidgetStatus.GRAY);
                    default -> { }
                }
            }

            // Support both the existing result JSON shape and the versioned envelope.
            Object payloadData = root.containsKey("data") ? root.get("data") : root;
            if (payloadData instanceof Map<?, ?> payloadMap)
            {
                Map<String, Object> data = objectMapper.convertValue(
                        payloadMap, new TypeReference<Map<String, Object>>() {});

                if (data.containsKey("metrics"))
                {
                    result.setMetrics(objectMapper.convertValue(data.get("metrics"),
                            new TypeReference<Map<String, Object>>() {}));
                }
                else
                {
                    Map<String, Object> scalarMetrics = new java.util.LinkedHashMap<>();
                    data.forEach((key, value) -> {
                        if (value == null || value instanceof String || value instanceof Number
                                || value instanceof Boolean)
                        {
                            scalarMetrics.put(key, value);
                        }
                    });
                    if (!scalarMetrics.isEmpty())
                    {
                        result.setMetrics(scalarMetrics);
                    }
                }

                if (data.containsKey("value") && result.getValue() == null)
                {
                    Object value = data.get("value");
                    if (value != null)
                    {
                        result.setValue(String.valueOf(value));
                    }
                }

                if (data.containsKey("columns"))
                {
                    result.setColumns(objectMapper.convertValue(data.get("columns"),
                            new TypeReference<List<WidgetColumn>>() {}));
                }

                if (data.containsKey("rows"))
                {
                    result.setRows(objectMapper.convertValue(data.get("rows"),
                            new TypeReference<List<Map<String, Object>>>() {}));
                }

                if (data.containsKey("details"))
                {
                    result.setDetails(objectMapper.convertValue(data.get("details"),
                            new TypeReference<Map<String, Object>>() {}));
                }

                Object chartData = data.get("data");
                if (chartData instanceof List<?> chartPoints
                        && chartPoints.stream().allMatch(point -> point instanceof Map<?, ?> pointMap
                                && pointMap.containsKey("label") && pointMap.containsKey("value")))
                {
                    result.setData(objectMapper.convertValue(chartData,
                            new TypeReference<List<WidgetDataPoint>>() {}));
                }
            }
            else if (payloadData instanceof List<?> chartPoints
                    && chartPoints.stream().allMatch(point -> point instanceof Map<?, ?> pointMap
                            && pointMap.containsKey("label") && pointMap.containsKey("value")))
            {
                result.setData(objectMapper.convertValue(payloadData,
                        new TypeReference<List<WidgetDataPoint>>() {}));
            }

            // Legacy result JSON stored these fields at the root.
            if (root.containsKey("metrics"))
            {
                result.setMetrics(objectMapper.convertValue(root.get("metrics"),
                        new TypeReference<Map<String, Object>>() {}));
            }
            if (root.containsKey("columns"))
            {
                result.setColumns(objectMapper.convertValue(root.get("columns"),
                        new TypeReference<List<WidgetColumn>>() {}));
            }
            if (root.containsKey("rows"))
            {
                result.setRows(objectMapper.convertValue(root.get("rows"),
                        new TypeReference<List<Map<String, Object>>>() {}));
            }
            if (root.containsKey("details"))
            {
                result.setDetails(objectMapper.convertValue(root.get("details"),
                        new TypeReference<Map<String, Object>>() {}));
            }
            if (root.containsKey("data") && root.get("data") instanceof List<?> chartPoints
                    && chartPoints.stream().allMatch(point -> point instanceof Map<?, ?> pointMap
                            && pointMap.containsKey("label") && pointMap.containsKey("value")))
            {
                result.setData(objectMapper.convertValue(root.get("data"),
                        new TypeReference<List<WidgetDataPoint>>() {}));
            }
        }
        catch (Exception exception)
        {
            result.setStatus(WidgetStatus.GRAY);
            if (result.getMessage() == null || result.getMessage().isBlank())
            {
                result.setMessage("Unable to read widget result JSON.");
            }
        }
    }

    @Transactional(readOnly = true)
    public DashboardWidgetResponse getWidgetResponse(Long widgetId)
    {
        DashboardWidget widget = getWidget(widgetId);

        MonitoringExecution execution = null;
        MonitoringResult monitoringResult = null;

        MonitoringJob job = null;

        if (widget.getDataSourceId() != null
                && ("MONITORING_RESULT".equals(widget.getDataSourceType())
                        || "MONITORING_JOB".equals(widget.getDataSourceType())))
        {
            Long sourceJobId = widget.getDataSourceId();

            if ("MONITORING_RESULT".equals(widget.getDataSourceType()))
            {
                MonitoringResult legacyResult = monitoringResultRepository.findById(widget.getDataSourceId()).orElse(null);
                sourceJobId = legacyResult == null || legacyResult.getExecution() == null
                        || legacyResult.getExecution().getMonitoringJob() == null
                                ? null : legacyResult.getExecution().getMonitoringJob().getId();
            }

            if (sourceJobId != null)
            {
                job = monitoringJobRepository.findById(sourceJobId).orElse(null);
                List<MonitoringExecution> executions =
                        monitoringExecutionRepository.findLatestByMonitoringJobIds(List.of(sourceJobId));

                if (!executions.isEmpty())
                {
                    execution = executions.get(0);
                    List<MonitoringResult> results =
                            monitoringResultRepository.findByExecutionIds(List.of(execution.getId()));
                    if (!results.isEmpty())
                    {
                        monitoringResult = results.get(0);
                    }
                }
            }
        }

        return createWidgetResponse(widget, job, execution, monitoringResult);
    }

    @Transactional(readOnly = true)
    public DashboardWidget getWidget(Long widgetId)
    {
        return dashboardWidgetRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard widget not found: " + widgetId));
    }
}
