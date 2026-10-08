package com.dxc.monitoring.controller;

import com.dxc.monitoring.service.SystemSettingService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes
{
    private final SystemSettingService systemSettingService;

    public GlobalModelAttributes(SystemSettingService systemSettingService)
    {
        this.systemSettingService = systemSettingService;
    }

    @ModelAttribute("applicationName")
    public String applicationName()
    {
        return systemSettingService.getValue(SystemSettingService.APPLICATION_NAME);
    }

    @ModelAttribute("applicationTheme")
    public String applicationTheme()
    {
        return systemSettingService.getTheme();
    }
}