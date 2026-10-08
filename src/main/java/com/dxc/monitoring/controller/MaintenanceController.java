package com.dxc.monitoring.controller;

import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.dxc.monitoring.service.SystemSettingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class MaintenanceController
{

    private final SystemSettingService systemSettingService;

    public MaintenanceController(SystemSettingService systemSettingService)
    {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/maintenance")
    public String maintenance(Model model)
    {
        model.addAttribute("maintenanceMessage",
                systemSettingService.getValue(SystemSettingService.MAINTENANCE_MESSAGE));

        return "maintenance";
    }

    @GetMapping("/maintenance/check")
    public String checkMaintenance(HttpServletRequest request, HttpServletResponse response)
    {

        systemSettingService.refreshSiteOfflineCache();

        if (systemSettingService.isSiteOffline())
        {
            return "redirect:/maintenance";
        }

        new SecurityContextLogoutHandler().logout(request, response, null);

        return "redirect:/login";
    }
}