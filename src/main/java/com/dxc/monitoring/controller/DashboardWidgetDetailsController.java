package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.service.MonitoringExecutionService;
import com.dxc.monitoring.service.dashboard.DashboardService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
public class DashboardWidgetDetailsController
{
    private final DashboardService dashboardService;
    private final MonitoringExecutionService monitoringExecutionService;
    private final PageRequestSupport pageRequestSupport;

    private static final java.util.Map<String, String> RESULT_SORT_FIELDS =
        java.util.Map.of("startedAt", "startedAt", "completedAt", "completedAt", "status", "status");

    public DashboardWidgetDetailsController(DashboardService dashboardService,
            MonitoringExecutionService monitoringExecutionService, PageRequestSupport pageRequestSupport)
    {
        this.dashboardService = dashboardService;
        this.monitoringExecutionService = monitoringExecutionService;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping("/dashboard/widgets/{id}/details")
    public String details(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "startedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, Model model)
    {
        var pageSpec = pageRequestSupport.create(page, size, sort, direction, "startedAt", RESULT_SORT_FIELDS);
        Pageable pageable = pageSpec.pageable();
        String normalizedSearch = search == null ? "" : search.trim();

        DashboardWidget widget = dashboardService.getWidget(id);

        if (!"MONITORING_JOB".equals(widget.getDataSourceType()) || widget.getDataSourceId() == null)
        {
            model.addAttribute("currentPage", "dashboard");
            model.addAttribute("widget", widget);
            model.addAttribute("history", Page.empty(pageable));
            model.addAttribute("pageSize", pageSpec.pageSize());
            model.addAttribute("search", normalizedSearch);
            model.addAttribute("sort", pageSpec.sort());
            model.addAttribute("direction", pageSpec.direction());
            return "dashboard-widget-details";
        }

        Page<MonitoringExecution> history =
            monitoringExecutionService.findByJobId(widget.getDataSourceId(), normalizedSearch, pageable);
        if (history.getTotalPages() > 0 && pageable.getPageNumber() >= history.getTotalPages()) {
            pageable = pageRequestSupport.create(history.getTotalPages() - 1, pageSpec.pageSize(),
                    pageSpec.sort(), pageSpec.direction(), "startedAt", RESULT_SORT_FIELDS).pageable();
            history = monitoringExecutionService.findByJobId(widget.getDataSourceId(), normalizedSearch, pageable);
        }

        List<MonitoringExecution> executions = history.getContent();
        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("widget", widget);
        model.addAttribute("history", history);
        model.addAttribute("executions", executions);
        model.addAttribute("pageSize", pageSpec.pageSize());
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("sort", pageSpec.sort());
        model.addAttribute("direction", pageSpec.direction());

        return "dashboard-widget-details";
    }
}
