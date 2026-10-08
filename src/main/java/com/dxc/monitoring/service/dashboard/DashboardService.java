package com.dxc.monitoring.service.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.DashboardWidgetResult;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.DashboardWidgetRepository;
import com.dxc.monitoring.repository.DashboardWidgetResultRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class DashboardService
{
    private final DashboardTabRepository dashboardTabRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final DashboardWidgetResultRepository dashboardWidgetResultRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(DashboardTabRepository dashboardTabRepository,
            DashboardWidgetRepository dashboardWidgetRepository,
            DashboardWidgetResultRepository dashboardWidgetResultRepository, ObjectMapper objectMapper)
    {
        this.dashboardTabRepository = dashboardTabRepository;
        this.dashboardWidgetRepository = dashboardWidgetRepository;
        this.dashboardWidgetResultRepository = dashboardWidgetResultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DashboardTabResponse> getDashboard()
    {
        List<DashboardTab> tabs = dashboardTabRepository.findAllByEnabledTrueOrderBySortOrderAsc();

        List<DashboardWidget> widgets =
            dashboardWidgetRepository.findAllByEnabledTrueOrderByTabSortOrderAscSortOrderAsc();

        List<Long> widgetIds = widgets.stream().filter(widget -> Boolean.TRUE.equals(widget.getStoreResult()))
                .map(DashboardWidget::getId).toList();

        Map<Long, DashboardWidgetResult> latestResults = new HashMap<>();

        if (!widgetIds.isEmpty())
        {
            dashboardWidgetResultRepository.findLatestResultsByWidgetIds(widgetIds)
                    .forEach(result -> latestResults.put(result.getWidget().getId(), result));
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

            DashboardWidgetResponse widgetResponse = createWidgetResponse(widget, latestResults.get(widget.getId()));

            tabResponse.getWidgets().add(widgetResponse);
        }

        List<DashboardTabResponse> result = new ArrayList<>();

        for (DashboardTab tab : tabs)
        {
            DashboardTabResponse response = tabResponses.get(tab.getId());

            if (response != null)
            {
                result.add(response);
            }
        }

        return result;
    }

    private DashboardWidgetResponse createWidgetResponse(DashboardWidget widget, DashboardWidgetResult storedResult)
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

        WidgetResult result = createResult(storedResult);

        DashboardWidgetResponse response = new DashboardWidgetResponse();

        response.setWidget(definition);
        response.setResult(result);

        return response;
    }

    private WidgetResult createResult(DashboardWidgetResult storedResult)
    {
        WidgetResult result = new WidgetResult();

        if (storedResult == null)
        {
            result.setStatus(WidgetStatus.GRAY);
            result.setMessage("No data");
            return result;
        }

        result.setStatus(parseStatus(storedResult.getStatus()));
        result.setMessage(storedResult.getMessage());
        result.setLastUpdated(storedResult.getResultTime());

        deserializeResultData(storedResult.getResultData(), result);

        return result;
    }

    private void deserializeResultData(String resultData, WidgetResult result)
    {
        if (resultData == null || resultData.isBlank())
        {
            return;
        }

        try
        {
            Map<String, Object> data = objectMapper.readValue(resultData, new TypeReference<Map<String, Object>>()
            {
            });

            if (data.containsKey("value"))
            {
                result.setValue(objectMapper.convertValue(data.get("value"), String.class));
            }

            if (data.containsKey("metrics"))
            {
                result.setMetrics(
                        objectMapper.convertValue(data.get("metrics"), new TypeReference<Map<String, Object>>()
                        {
                        }));
            }

            if (data.containsKey("columns"))
            {
                result.setColumns(objectMapper.convertValue(data.get("columns"), new TypeReference<List<WidgetColumn>>()
                {
                }));
            }

            if (data.containsKey("rows"))
            {
                result.setRows(
                        objectMapper.convertValue(data.get("rows"), new TypeReference<List<Map<String, Object>>>()
                        {
                        }));
            }

            if (data.containsKey("data"))
            {
                result.setData(objectMapper.convertValue(data.get("data"), new TypeReference<List<WidgetDataPoint>>()
                {
                }));
            }

            if (data.containsKey("details"))
            {
                result.setDetails(
                        objectMapper.convertValue(data.get("details"), new TypeReference<Map<String, Object>>()
                        {
                        }));
            }
        } catch (Exception exception)
        {
            result.setStatus(WidgetStatus.GRAY);

            if (result.getMessage() == null || result.getMessage().isBlank())
            {
                result.setMessage("Unable to read widget result");
            }
        }
    }

    private WidgetStatus parseStatus(String status)
    {
        if (status == null)
        {
            return WidgetStatus.GRAY;
        }

        try
        {
            return WidgetStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException exception)
        {
            return WidgetStatus.GRAY;
        }
    }

    @Transactional(readOnly = true)
    public DashboardWidget getWidget(Long widgetId)
    {
        return dashboardWidgetRepository.findById(widgetId)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard widget not found: " + widgetId));
    }

    @Transactional(readOnly = true)
    public List<DashboardWidgetResult> getWidgetHistory(Long widgetId)
    {
        return dashboardWidgetResultRepository.findAllByWidgetIdOrderByResultTimeDesc(widgetId);
    }

    @Transactional(readOnly = true)
    public WidgetResult getWidgetResult(DashboardWidgetResult storedResult)
    {
        return createResult(storedResult);
    }
}