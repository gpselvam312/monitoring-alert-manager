package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

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
        MenuItem monitoring = item(1L, "MONITORING_JOBS", "Monitoring Jobs", "/monitoring/jobs",
                null, "MONITORING_VIEW");
        MenuItem machines = item(2L, "MACHINES", "Machines", "/monitoring/machines",
                null, "MACHINE_VIEW");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(monitoring, machines));

        var authentication = new UsernamePasswordAuthenticationToken("operator", "n/a",
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")));

        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);

        assertEquals(1, sections.size());
        assertEquals(1, sections.get(0).getItems().size());
        assertEquals("monitoring-jobs", sections.get(0).getItems().get(0).getMenuKey());
    }

    @Test
    void rendersChildrenUnderTheirConfiguredParent()
    {
        MenuItem alerts = item(5L, "ALERTS", "Alerts", null, null, null);
        MenuItem activeAlerts = item(7L, "ACTIVE_ALERTS", "Active Alerts", "/alerts", 5L, "ALERT_VIEW");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(alerts, activeAlerts));

        var authentication = new UsernamePasswordAuthenticationToken("operator", "n/a",
                List.of(new SimpleGrantedAuthority("ALERT_VIEW")));

        // Alert routes are exposed once the corresponding pages are implemented.
        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);
        assertEquals(1, sections.size());
        assertEquals("Alerts", sections.get(0).getName());
        assertEquals(1, sections.get(0).getItems().size());
        assertEquals("active-alerts", sections.get(0).getItems().get(0).getMenuKey());
        assertEquals("/alerts", sections.get(0).getItems().get(0).getHref());
    }

    @Test
    void requiresAdminRoleForUserAdministration()
    {
        MenuItem administration = item(6L, "ADMINISTRATION", "Administration", null, null, "SYSTEM_CONFIG");
        MenuItem users = item(11L, "USERS", "Users", "/administration/users", 6L, "USER_CONFIG");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(administration, users));

        var nonAdmin = new UsernamePasswordAuthenticationToken("operator", "n/a",
                List.of(new SimpleGrantedAuthority("USER_CONFIG"), new SimpleGrantedAuthority("SYSTEM_CONFIG")));

        assertEquals(0, menuItemService.getVisibleSections(nonAdmin).size());
    }

    @Test
    void keepsDashboardVisibleWhenItsPermissionIsGranted()
    {
        MenuItem dashboard = item(1L, "DASHBOARD", "Dashboard", "/dashboard", null, "MONITORING_VIEW");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(dashboard));

        var authentication = new UsernamePasswordAuthenticationToken("viewer", "n/a",
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")));

        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);

        assertEquals("/dashboard", sections.get(0).getItems().get(0).getHref());
    }

    @Test
    void displaysConfiguredLocalRouteForCustomMenuKey()
    {
        MenuItem custom = item(14L, "CUSTOM_TOOL", "Custom Tool", "/administration/custom-tool",
                null, "SYSTEM_CONFIG");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(custom));

        var authentication = new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("SYSTEM_CONFIG"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        List<MenuItemService.SidebarSection> sections = menuItemService.getVisibleSections(authentication);

        assertEquals(1, sections.size());
        assertEquals("/administration/custom-tool", sections.get(0).getItems().get(0).getHref());
    }

    @Test
    void doesNotDisplayExternalMenuUrls()
    {
        MenuItem external = item(15L, "EXTERNAL_TOOL", "External Tool", "https://example.com",
                null, "MONITORING_VIEW");
        when(menuItemRepository.findByEnabledTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(external));

        var authentication = new UsernamePasswordAuthenticationToken("viewer", "n/a",
                List.of(new SimpleGrantedAuthority("MONITORING_VIEW")));

        assertEquals(0, menuItemService.getVisibleSections(authentication).size());
    }

    private MenuItem item(Long id, String key, String name, String url, Long parentId, String permission)
    {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setMenuKey(key);
        item.setMenuName(name);
        item.setUrl(url);
        item.setParentId(parentId);
        item.setIcon("bi-activity");
        item.setDisplayOrder(id.intValue());
        item.setRequiredPermission(permission);
        item.setEnabled(true);
        return item;
    }
}
