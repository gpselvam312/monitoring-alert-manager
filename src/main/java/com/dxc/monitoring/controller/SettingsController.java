package com.dxc.monitoring.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.SystemSetting;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.SystemSettingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/administration/settings")
@PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
public class SettingsController
{
    private final SystemSettingService systemSettingService;

    public SettingsController(SystemSettingService systemSettingService)
    {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public String settings(Model model)
    {
        List<SystemSetting> settings = systemSettingService.findAll();

        Map<String, String> values = settings.stream().collect(Collectors.toMap(SystemSetting::getSettingKey,
                SystemSetting::getSettingValue, (existing, replacement) -> replacement, java.util.HashMap::new));

        model.addAttribute("applicationName", values.get(SystemSettingService.APPLICATION_NAME));

        model.addAttribute("theme", values.get(SystemSettingService.THEME));

        model.addAttribute("siteOffline", Boolean.parseBoolean(values.get(SystemSettingService.SITE_OFFLINE)));

        model.addAttribute("maintenanceMessage", values.get(SystemSettingService.MAINTENANCE_MESSAGE));

        model.addAttribute("maintenanceBypassIps", values.get(SystemSettingService.MAINTENANCE_BYPASS_IPS));

        model.addAttribute("currentPage", "settings");

        return "administration/settings";
    }

    @PostMapping
    public String saveSettings(String applicationName, String theme, boolean siteOffline, String maintenanceMessage,
            String maintenanceBypassIps, Authentication authentication, RedirectAttributes redirectAttributes)
    {
        try
        {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            systemSettingService.updateSettings(applicationName, theme, siteOffline, maintenanceMessage,
                    maintenanceBypassIps, userDetails.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");
        } catch (IllegalArgumentException ex)
        {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/administration/settings";
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
