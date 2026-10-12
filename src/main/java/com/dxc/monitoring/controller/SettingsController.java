package com.dxc.monitoring.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.SystemSetting;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.SystemSettingService;

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
        model.addAttribute("settings", settings);
        model.addAttribute("currentPage", "settings");
        return "administration/settings";
    }

    @PostMapping
    public String saveSettings(@RequestParam Map<String, String> requestParameters,
            Authentication authentication, RedirectAttributes redirectAttributes)
    {
        try
        {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Map<String, String> values = new LinkedHashMap<>();
            requestParameters.forEach((name, value) ->
            {
                if (name.startsWith("value_"))
                    values.put(name.substring("value_".length()), value);
            });

            systemSettingService.updateSettings(values, userDetails.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");
        }
        catch (IllegalArgumentException exception)
        {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/administration/settings";
    }
}
