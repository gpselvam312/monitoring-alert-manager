package com.dxc.monitoring.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dxc.monitoring.service.dashboard.DashboardAccessService;
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
    private final DashboardAccessService dashboardAccessService;
    private final MonitoringExecutionManager monitoringExecutionManager;
    private final MonitoringJobService monitoringJobService;

    public DashboardController(DashboardService dashboardService, DashboardAccessService dashboardAccessService,
            MonitoringExecutionManager monitoringExecutionManager, MonitoringJobService monitoringJobService)
    {
        this.dashboardService = dashboardService;
        this.dashboardAccessService = dashboardAccessService;
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
    @GetMapping("/api/dashboard/applications")
    public ResponseEntity<List<Map<String, Object>>> getApplications()
    {
        Long primaryApplicationId = dashboardAccessService.getPrimaryApplicationId();
        List<Map<String, Object>> items = dashboardAccessService.getAccessibleApplications().stream()
                .map(app -> Map.<String, Object>of(
                        "id", app.getId(),
                        "name", app.getName(),
                        "primary", app.getId().equals(primaryApplicationId)))
                .toList();
        return ResponseEntity.ok(items);
    }

    @ResponseBody
    @GetMapping("/api/dashboard/environments")
    public ResponseEntity<List<Map<String, Object>>> getEnvironments(
            @org.springframework.web.bind.annotation.RequestParam Long applicationId)
    {
        List<Map<String, Object>> items = dashboardAccessService.getEnvironmentsForApplication(applicationId).stream()
                .map(env -> Map.<String, Object>of("id", env.getId(), "name", env.getName())).toList();
        return ResponseEntity.ok(items);
    }

    @ResponseBody
    @GetMapping("/api/dashboard")
    public ResponseEntity<List<DashboardTabResponse>> getDashboard(
            @org.springframework.web.bind.annotation.RequestParam Long applicationId,
            @org.springframework.web.bind.annotation.RequestParam Long environmentId)
    {
        return ResponseEntity.ok(dashboardService.getDashboard(applicationId, environmentId));
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
    public ResponseEntity<DashboardWidgetResponse> runWidget(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String endDate)
    {
        DashboardWidget widget = dashboardService.getWidget(id);

        if (!"MONITORING_JOB".equals(widget.getDataSourceType()) || widget.getDataSourceId() == null)
        {
            throw new IllegalArgumentException("Dashboard widget is not configured with a monitoring job.");
        }

        MonitoringJob job = monitoringJobService.findById(widget.getDataSourceId());
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("The monitoring job is not assigned to an application.");
        dashboardAccessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_RUN");

        if (widget.getTab().getEnvironment() == null || !widget.getTab().getEnvironment().isEnabled()
                || widget.getTab().getApplication() == null || job.getApplication() == null
                || !widget.getTab().getApplication().getId().equals(job.getApplication().getId())
                || job.getEnvironment() == null
                || !widget.getTab().getEnvironment().getId().equals(job.getEnvironment().getId()))
        {
            throw new IllegalStateException("The monitoring job does not belong to this dashboard environment.");
        }

        if (!job.isManualRunEnabled())
        {
            throw new IllegalStateException("Manual execution is disabled for this monitoring job.");
        }

        boolean hasDateRange = startDate != null || endDate != null;
        if (!hasDateRange && dashboardService.isDateRangeEnabled(widget))
        {
            throw new IllegalArgumentException("Select a date range and use Apply for this API widget.");
        }

        if (hasDateRange)
        {
            if (startDate == null || endDate == null)
            {
                throw new IllegalArgumentException("Both startDate and endDate are required.");
            }
            if (job.getType() != MonitoringJob.MonitorType.API || !dashboardService.isDateRangeEnabled(widget))
            {
                throw new IllegalArgumentException("Date-range execution is not enabled for this API widget.");
            }
            if (job.getUrl() == null || !job.getUrl().contains("{{startDate}}")
                    || !job.getUrl().contains("{{endDate}}"))
            {
                throw new IllegalArgumentException(
                        "The API URL must include both {{startDate}} and {{endDate}} placeholders.");
            }

            try
            {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                if (start.isAfter(end))
                {
                    throw new IllegalArgumentException("Start date must be on or before end date.");
                }
            }
            catch (DateTimeParseException exception)
            {
                throw new IllegalArgumentException("Dates must use YYYY-MM-DD format.");
            }

            monitoringExecutionManager.execute(job, Map.of("startDate", startDate, "endDate", endDate));
        }
        else
        {
            monitoringExecutionManager.execute(job);
        }

        return ResponseEntity.ok(dashboardService.getWidgetResponse(id));
    }

}