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
        List<DashboardTab> tabs = dashboardTabRepository.findAllByEnabledTrueOrderBySortOrderAsc();
        List<DashboardWidget> widgets =
            dashboardWidgetRepository.findAllByEnabledTrueOrderByTabSortOrderAscSortOrderAsc();

        List<Long> jobIds = widgets.stream()
                .filter(widget -> "MONITORING_JOB".equals(widget.getDataSourceType()))
                .map(DashboardWidget::getDataSourceId)
                .filter(id -> id != null)
                .distinct()
                .toList();

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
            monitoringResultRepository.findByExecutionIds(executionIds).forEach(result ->
            {
                latestResults.putIfAbsent(result.getExecution().getId(), result);
            });
        }

        Map<Long, DashboardTabResponse> tabResponses = new HashMap<>();

        for (DashboardTab tab : tabs)
        {
            DashboardTabResponse response = new DashboardTabResponse();
            response.setId(tab.getId());
            response.setName(tab.getName());
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

            MonitoringExecution execution = latestExecutions.get(widget.getDataSourceId());
            MonitoringResult result = execution == null ? null : latestResults.get(execution.getId());
            MonitoringJob job = jobs.get(widget.getDataSourceId());

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
        definition.setJobRunning(execution != null
                && execution.getStatus() == MonitoringExecution.ExecutionStatus.RUNNING);
        definition.setRunNowEnabled(canRunNow(widget, job, execution));

        WidgetResult result = createResult(execution, monitoringResult);

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
        result.setLastUpdated(execution.getCompletedAt() != null
                ? execution.getCompletedAt()
                : execution.getStartedAt());

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
                || !job.isEnabled())
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
            Map<String, Object> data = objectMapper.readValue(resultData, new TypeReference<Map<String, Object>>() {});

            if (data.containsKey("metrics"))
            {
                result.setMetrics(objectMapper.convertValue(data.get("metrics"),
                        new TypeReference<Map<String, Object>>() {}));
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

            if (data.containsKey("data"))
            {
                result.setData(objectMapper.convertValue(data.get("data"),
                        new TypeReference<List<WidgetDataPoint>>() {}));
            }

            if (data.containsKey("details"))
            {
                result.setDetails(objectMapper.convertValue(data.get("details"),
                        new TypeReference<Map<String, Object>>() {}));
            }
        }
        catch (Exception exception)
        {
            result.setStatus(WidgetStatus.GRAY);
            if (result.getMessage() == null || result.getMessage().isBlank())
            {
                result.setMessage("Unable to read widget result");
            }
        }
    }

    @Transactional(readOnly = true)
    public DashboardWidgetResponse getWidgetResponse(Long widgetId)
    {
        DashboardWidget widget = getWidget(widgetId);

        MonitoringExecution execution = null;
        MonitoringResult monitoringResult = null;

        if ("MONITORING_JOB".equals(widget.getDataSourceType()) && widget.getDataSourceId() != null)
        {
            List<MonitoringExecution> executions =
                    monitoringExecutionRepository.findLatestByMonitoringJobIds(List.of(widget.getDataSourceId()));

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

        MonitoringJob job = widget.getDataSourceId() == null ? null
                : monitoringJobRepository.findById(widget.getDataSourceId()).orElse(null);

        return createWidgetResponse(widget, job, execution, monitoringResult);
    }

    @Transactional(readOnly = true)
    public DashboardWidget getWidget(Long widgetId)
    {
        return dashboardWidgetRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard widget not found: " + widgetId));
    }
}
