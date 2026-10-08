package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dxc.monitoring.service.dashboard.DashboardService;
import com.dxc.monitoring.service.dashboard.DashboardWidgetResponse;
import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.service.MonitoringExecutionManager;
import com.dxc.monitoring.service.MonitoringJobService;
import com.dxc.monitoring.service.dashboard.DashboardTabResponse;

@Controller
public class DashboardController
{
    private final DashboardService dashboardService;
    private final MonitoringExecutionManager monitoringExecutionManager;
    private final MonitoringJobService monitoringJobService;

    public DashboardController(DashboardService dashboardService,
            MonitoringExecutionManager monitoringExecutionManager,
            MonitoringJobService monitoringJobService)
    {
        this.dashboardService = dashboardService;
        this.monitoringExecutionManager = monitoringExecutionManager;
        this.monitoringJobService = monitoringJobService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model)
    {
        model.addAttribute("currentPage", "dashboard");
        return "dashboard";
    }

    @GetMapping("/")
    public String home()
    {
        return "redirect:/dashboard";
    }

    @ResponseBody
    @GetMapping("/api/dashboard")
    public ResponseEntity<List<DashboardTabResponse>> getDashboard()
    {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    @ResponseBody
    @GetMapping("/api/dashboard/widgets/{id}")
    public ResponseEntity<DashboardWidgetResponse> getWidget(@PathVariable Long id)
    {
        return ResponseEntity.ok(dashboardService.getWidgetResponse(id));
    }

    @ResponseBody
    @PostMapping("/api/dashboard/widgets/{id}/run")
    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    public ResponseEntity<DashboardWidgetResponse> runWidget(@PathVariable Long id)
    {
        DashboardWidget widget = dashboardService.getWidget(id);

        if (!"MONITORING_JOB".equals(widget.getDataSourceType()) || widget.getDataSourceId() == null)
        {
            throw new IllegalArgumentException("Dashboard widget is not configured with a monitoring job.");
        }

        MonitoringJob job = monitoringJobService.findById(widget.getDataSourceId());

        if (!job.isManualRunEnabled())
        {
            throw new IllegalStateException("Manual execution is disabled for this monitoring job.");
        }

        monitoringExecutionManager.execute(job);

        return ResponseEntity.ok(dashboardService.getWidgetResponse(id));
    }

}