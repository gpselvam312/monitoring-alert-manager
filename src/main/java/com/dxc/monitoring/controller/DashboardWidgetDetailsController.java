package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.service.MonitoringExecutionService;
import com.dxc.monitoring.service.dashboard.DashboardService;

@Controller
public class DashboardWidgetDetailsController
{
    private final DashboardService dashboardService;
    private final MonitoringExecutionService monitoringExecutionService;

    private static final java.util.Map<String, String> RESULT_SORT_FIELDS =
        java.util.Map.of("startedAt", "startedAt", "completedAt", "completedAt", "status", "status");

    public DashboardWidgetDetailsController(DashboardService dashboardService,
            MonitoringExecutionService monitoringExecutionService)
    {
        this.dashboardService = dashboardService;
        this.monitoringExecutionService = monitoringExecutionService;
    }

    @GetMapping("/dashboard/widgets/{id}/details")
    public String details(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "startedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, Model model)
    {
        if (page < 0) page = 0;
        if (size != 5 && size != 10 && size != 25) size = 5;

        String normalizedSearch = search == null ? "" : search.trim();
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDirection, RESULT_SORT_FIELDS.getOrDefault(sort, "startedAt")));

        DashboardWidget widget = dashboardService.getWidget(id);

        if (!"MONITORING_JOB".equals(widget.getDataSourceType()) || widget.getDataSourceId() == null)
        {
            model.addAttribute("currentPage", "dashboard");
            model.addAttribute("widget", widget);
            model.addAttribute("history", Page.empty(pageable));
            model.addAttribute("pageSize", size);
            model.addAttribute("search", normalizedSearch);
            model.addAttribute("sort", sort);
            model.addAttribute("direction", direction);
            return "dashboard-widget-details";
        }

        Page<MonitoringExecution> history =
            monitoringExecutionService.findByJobId(widget.getDataSourceId(), normalizedSearch, pageable);

        List<MonitoringExecution> executions = history.getContent();
        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("widget", widget);
        model.addAttribute("history", history);
        model.addAttribute("executions", executions);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);

        return "dashboard-widget-details";
    }
}
