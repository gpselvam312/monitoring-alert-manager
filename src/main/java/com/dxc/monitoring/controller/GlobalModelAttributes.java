package com.dxc.monitoring.controller;

import com.dxc.monitoring.service.MenuItemService;
import com.dxc.monitoring.service.SystemSettingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalModelAttributes
{
    private final SystemSettingService systemSettingService;
    private final MenuItemService menuItemService;

    public GlobalModelAttributes(SystemSettingService systemSettingService, MenuItemService menuItemService)
    {
        this.systemSettingService = systemSettingService;
        this.menuItemService = menuItemService;
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

    @ModelAttribute("sidebarSections")
    public List<MenuItemService.SidebarSection> sidebarSections(Authentication authentication)
    {
        return menuItemService.getVisibleSections(authentication);
    }
}
