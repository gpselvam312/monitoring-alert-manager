package com.dxc.monitoring.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.dxc.monitoring.service.MenuUrlPolicy;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuItemService {
    private final MenuDefinitionCacheService menuCache;

    public MenuItemService(MenuDefinitionCacheService menuCache) {
        this.menuCache = menuCache;
    }

    @Transactional(readOnly = true)
    public List<SidebarSection> getVisibleSections(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return List.of();
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        if (authorities.contains("ROLE_ANONYMOUS")) return List.of();

        List<MenuDefinitionCacheService.MenuDefinition> items = menuCache.getEnabledMenuDefinitions();
        Map<Long, List<MenuDefinitionCacheService.MenuDefinition>> childrenByParent = new java.util.HashMap<>();
        List<MenuDefinitionCacheService.MenuDefinition> roots = new ArrayList<>();
        for (var item : items) {
            if (item.parentId() == null) roots.add(item);
            else childrenByParent.computeIfAbsent(item.parentId(), ignored -> new ArrayList<>()).add(item);
        }

        Map<String, SidebarSection> sections = new LinkedHashMap<>();
        for (var root : roots) {
            String rootUrl = routeFor(root.url());
            if (rootUrl != null && isVisible(root, authorities)) {
                String sectionName = sectionName(root.sectionName(), "Main");
                sections.computeIfAbsent(sectionName, name -> new SidebarSection(name, new ArrayList<>()))
                        .getItems().add(toLink(root, rootUrl));
            }
            List<MenuDefinitionCacheService.MenuDefinition> children =
                    childrenByParent.getOrDefault(root.id(), List.of());
            if (!children.isEmpty()) {
                String sectionName = sectionName(root.sectionName(), root.menuName());
                for (var child : children) {
                    String href = routeFor(child.url());
                    if (href != null && isVisible(child, authorities)) {
                        sections.computeIfAbsent(sectionName, name -> new SidebarSection(name, new ArrayList<>()))
                                .getItems().add(toLink(child, href));
                    }
                }
            }
        }
        return sections.values().stream().filter(section -> !section.getItems().isEmpty()).toList();
    }

    private boolean isVisible(MenuDefinitionCacheService.MenuDefinition item, Set<String> authorities) {
        String requiredPermission = item.requiredPermission();
        return requiredPermission == null || requiredPermission.isBlank() || authorities.contains(requiredPermission);
    }

    private String sectionName(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }

    private String routeFor(String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) return null;
        String url = configuredUrl.trim();
        if (!MenuUrlPolicy.isSafeLocalPath(url)) return null;
        return url;
    }

    private SidebarLink toLink(MenuDefinitionCacheService.MenuDefinition item, String href) {
        return new SidebarLink(item.menuKey().toLowerCase(Locale.ROOT).replace('_', '-'),
                item.menuName(), item.icon(), href);
    }

    public static final class SidebarSection {
        private final String name;
        private final List<SidebarLink> items;
        public SidebarSection(String name, List<SidebarLink> items) { this.name = name; this.items = items; }
        public String getName() { return name; }
        public List<SidebarLink> getItems() { return items; }
    }

    public static final class SidebarLink {
        private final String menuKey;
        private final String label;
        private final String icon;
        private final String href;
        public SidebarLink(String menuKey, String label, String icon, String href) {
            this.menuKey = menuKey; this.label = label; this.icon = icon; this.href = href;
        }
        public String getMenuKey() { return menuKey; }
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public String getHref() { return href; }
    }
}
