package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dxc.monitoring.service.dashboard.DashboardService;
import com.dxc.monitoring.service.dashboard.DashboardTabResponse;

@Controller
public class DashboardController
{
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService)
    {
        this.dashboardService = dashboardService;
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
}