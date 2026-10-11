package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest
{
    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuItemService menuItemService;

    @Test
    void showsOnlyItemsAllowedByGrantedAuthorities()
    {
        MenuItem monitoring = item("monitoring-jobs", "Monitoring", "MONITORING_VIEW");
        MenuItem machines = item("machines", "Monitoring", "MACHINE_VIEW");
        when(menuItemRepository.findByEnabledTrueOrderBySectionOrderAscDisplayOrderAsc())
                .thenReturn(List.of(monitoring, machines));

        var authentication = new UsernamePasswordAuthenticationToken("operator", "n/a",
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")));

        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);

        assertEquals(1, sections.size());
        assertEquals(1, sections.get(0).getItems().size());
        assertEquals("monitoring-jobs", sections.get(0).getItems().get(0).getMenuKey());
    }

    @Test
    void keepsUnrestrictedDashboardVisibleToAuthenticatedUsers()
    {
        MenuItem dashboard = new MenuItem();
        dashboard.setMenuKey("dashboard");
        dashboard.setRouteKey("dashboard");
        dashboard.setLabel("Dashboard");
        dashboard.setIcon("bi-grid-1x2");
        dashboard.setSectionName("Main");
        dashboard.setEnabled(true);
        when(menuItemRepository.findByEnabledTrueOrderBySectionOrderAscDisplayOrderAsc())
                .thenReturn(List.of(dashboard));

        var authentication = new UsernamePasswordAuthenticationToken("viewer", "n/a",
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")));

        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);

        assertEquals("/dashboard", sections.get(0).getItems().get(0).getHref());
    }

    @Test
    void requiresAdminAuthorityForAdminOnlyItems()
    {
        MenuItem users = new MenuItem();
        users.setMenuKey("users");
        users.setRouteKey("users");
        users.setLabel("Users");
        users.setIcon("bi-person-gear");
        users.setSectionName("Administration");
        users.setRequiredRole("ROLE_ADMIN");
        users.setEnabled(true);
        when(menuItemRepository.findByEnabledTrueOrderBySectionOrderAscDisplayOrderAsc())
                .thenReturn(List.of(users));

        var viewer = new UsernamePasswordAuthenticationToken("viewer", "n/a",
                List.of(new SimpleGrantedAuthority("USER_CONFIG")));

        assertEquals(0, menuItemService.getVisibleSections(viewer).size());
    }

    private MenuItem item(String key, String section, String permissionName)
    {
        Permission permission = new Permission();
        permission.setName(permissionName);

        MenuItem item = new MenuItem();
        item.setMenuKey(key);
        item.setRouteKey(key);
        item.setLabel(key);
        item.setIcon("bi-activity");
        item.setSectionName(section);
        item.setEnabled(true);
        item.setPermissions(Set.of(permission));
        return item;
    }
}
