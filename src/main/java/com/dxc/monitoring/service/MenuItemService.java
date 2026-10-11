package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuItemService
{
    private static final Set<String> ADMIN_ONLY_MENUS = Set.of("USERS", "ROLES", "MENU_MANAGEMENT");
    private static final Set<String> NOT_YET_SUPPORTED_MENUS = Set.of("ACTIVE_ALERTS", "ALERT_HISTORY", "AUDIT_LOG");

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

        List<MenuItem> items = menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc();
        Map<String, SidebarSection> sections = new LinkedHashMap<>();
        for (MenuItem item : items)
        {
            if (item.getParentId() != null)
            {
                continue;
            }

            String href = routeFor(item);
            if (href != null && isVisible(item, authorities))
            {
                String sectionName = rootSection(item.getMenuKey());
                sections.computeIfAbsent(sectionName, name -> new SidebarSection(name, new ArrayList<>()))
                        .getItems().add(toLink(item, href));
            }

            List<MenuItem> children = items.stream()
                    .filter(child -> item.getId() != null && item.getId().equals(child.getParentId()))
                    .filter(child -> routeFor(child) != null && isVisible(child, authorities))
                    .toList();

            if (!children.isEmpty())
            {
                SidebarSection section = sections.computeIfAbsent(item.getMenuName(),
                        name -> new SidebarSection(name, new ArrayList<>()));
                for (MenuItem child : children)
                {
                    section.getItems().add(toLink(child, routeFor(child)));
                }
            }
        }

        return sections.values().stream()
                .filter(section -> !section.getItems().isEmpty())
                .toList();
    }

    private boolean isVisible(MenuItem item, Set<String> authorities)
    {
        String requiredPermission = item.getRequiredPermission();
        if (requiredPermission != null && !requiredPermission.isBlank()
                && !authorities.contains(requiredPermission))
        {
            return false;
        }

        return !ADMIN_ONLY_MENUS.contains(item.getMenuKey()) || authorities.contains("ROLE_ADMIN");
    }

    private String routeFor(MenuItem item)
    {
        if (item.getUrl() == null || item.getUrl().isBlank()
                || NOT_YET_SUPPORTED_MENUS.contains(item.getMenuKey()))
        {
            return null;
        }

        String url = item.getUrl().trim();
        // Only allow local application paths; do not allow external URLs or script schemes.
        if (!url.startsWith("/") || url.startsWith("//") || url.contains("..")
                || url.contains("\\") || url.contains("\r") || url.contains("\n"))
        {
            return null;
        }
        return url;
    }

    private String rootSection(String menuKey)
    {
        return switch (menuKey)
        {
            case "DASHBOARD" -> "Main";
            case "MONITORING_JOBS", "STREAMING_JOBS", "MACHINES" -> "Monitoring";
            case "SCHEDULES" -> "Scheduling";
            default -> "Main";
        };
    }

    private SidebarLink toLink(MenuItem item, String href)
    {
        return new SidebarLink(item.getMenuKey().toLowerCase(Locale.ROOT).replace('_', '-'),
                item.getMenuName(), item.getIcon(), href);
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
