package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuItemService
{
    /*
     * Route keys are intentionally resolved in application code. Database rows
     * cannot introduce arbitrary URLs or external links into the sidebar.
     */
    private static final Map<String, String> ROUTES = Map.ofEntries(
            Map.entry("dashboard", "/dashboard"),
            Map.entry("monitoring-jobs", "/monitoring/jobs"),
            Map.entry("streaming-jobs", "/monitoring/streaming-jobs"),
            Map.entry("machines", "/monitoring/machines"),
            Map.entry("schedules", "/scheduling/schedules"),
            Map.entry("applications", "/administration/applications"),
            Map.entry("environments", "/administration/environments"),
            Map.entry("dashboard-configuration", "/administration/dashboard"),
            Map.entry("users", "/administration/users"),
            Map.entry("roles", "/administration/roles"),
            Map.entry("settings", "/administration/settings"));

    private final MenuItemRepository menuItemRepository;

    public MenuItemService(MenuItemRepository menuItemRepository)
    {
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SidebarSection> getVisibleSections(Authentication authentication)
    {
        if (authentication == null || !authentication.isAuthenticated())
        {
            return List.of();
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());

        if (authorities.contains("ROLE_ANONYMOUS"))
        {
            return List.of();
        }

        Map<String, SidebarSection> sections = new LinkedHashMap<>();
        for (MenuItem item : menuItemRepository.findByEnabledTrueOrderBySectionOrderAscDisplayOrderAsc())
        {
            String href = ROUTES.get(item.getRouteKey());
            if (href == null || !isVisible(item, authorities))
            {
                continue;
            }

            SidebarSection section = sections.computeIfAbsent(item.getSectionName(),
                    name -> new SidebarSection(name, new ArrayList<>()));
            section.getItems().add(new SidebarLink(item.getMenuKey(), item.getLabel(), item.getIcon(), href));
        }

        return List.copyOf(sections.values());
    }

    private boolean isVisible(MenuItem item, Set<String> authorities)
    {
        if (item.getRequiredRole() != null && !item.getRequiredRole().isBlank())
        {
            return authorities.contains(item.getRequiredRole());
        }

        return item.getPermissions().isEmpty()
                || item.getPermissions().stream().anyMatch(permission -> authorities.contains(permission.getName()));
    }

    public static final class SidebarSection
    {
        private final String name;
        private final List<SidebarLink> items;

        public SidebarSection(String name, List<SidebarLink> items)
        {
            this.name = name;
            this.items = items;
        }

        public String getName() { return name; }
        public List<SidebarLink> getItems() { return items; }
    }

    public static final class SidebarLink
    {
        private final String menuKey;
        private final String label;
        private final String icon;
        private final String href;

        public SidebarLink(String menuKey, String label, String icon, String href)
        {
            this.menuKey = menuKey;
            this.label = label;
            this.icon = icon;
            this.href = href;
        }

        public String getMenuKey() { return menuKey; }
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public String getHref() { return href; }
    }
}
