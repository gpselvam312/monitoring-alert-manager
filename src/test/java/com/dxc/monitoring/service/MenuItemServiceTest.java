package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuItemServiceTest {
    private MenuItemRepository repository;
    private MenuItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(MenuItemRepository.class);
        service = new MenuItemService(new MenuDefinitionCacheService(repository));
    }

    @Test void showsOnlyItemsAllowedByGrantedAuthorities() {
        MenuItem monitoring = item(1L,"MONITORING_JOBS","Monitoring Jobs","/monitoring/jobs",null,"MONITORING_VIEW");
        MenuItem machines = item(2L,"MACHINES","Machines","/monitoring/machines",null,"MACHINE_VIEW");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(monitoring,machines));
        var authentication = auth("MONITORING_VIEW");
        var sections = service.getVisibleSections(authentication);
        assertEquals(1,sections.size());
        assertEquals(1,sections.get(0).getItems().size());
        assertEquals("monitoring-jobs",sections.get(0).getItems().get(0).getMenuKey());
    }

    @Test void rendersChildrenUnderTheirConfiguredParent() {
        MenuItem alerts=item(5L,"ALERTS","Alerts",null,null,null);
        MenuItem active=item(7L,"ACTIVE_ALERTS","Active Alerts","/alerts",5L,"ALERT_VIEW");
        alerts.setSectionName("Operations");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(alerts,active));
        var sections=service.getVisibleSections(auth("ALERT_VIEW"));
        assertEquals(1,sections.size());
        assertEquals("Operations",sections.get(0).getName());
        assertEquals(1,sections.get(0).getItems().size());
        assertEquals("active-alerts",sections.get(0).getItems().get(0).getMenuKey());
    }

    @Test void requiresConfiguredAdminAuthorityForUserAdministration() {
        MenuItem administration=item(6L,"ADMINISTRATION","Administration",null,null,"SYSTEM_CONFIG");
        MenuItem users=item(11L,"USERS","Users","/administration/users",6L,"ROLE_ADMIN");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(administration,users));
        assertEquals(0,service.getVisibleSections(auth("USER_CONFIG","SYSTEM_CONFIG")).size());
        assertEquals(1,service.getVisibleSections(auth("ROLE_ADMIN","SYSTEM_CONFIG")).size());
    }

    @Test void supportsNewRolesByUsingConfiguredPermissionWithoutRoleNameChecks() {
        MenuItem custom=item(14L,"CUSTOM_TOOL","Custom Tool","/administration/custom-tool",null,"CUSTOM_TOOL_VIEW");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(custom));
        var sections=service.getVisibleSections(auth("CUSTOM_TOOL_VIEW"));
        assertEquals(1,sections.size());
        assertEquals("/administration/custom-tool",sections.get(0).getItems().get(0).getHref());
    }

    @Test void keepsDashboardVisibleWhenItsPermissionIsGranted() {
        MenuItem dashboard=item(1L,"DASHBOARD","Dashboard","/dashboard",null,"MONITORING_VIEW");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(dashboard));
        var sections=service.getVisibleSections(auth("MONITORING_VIEW"));
        assertEquals("/dashboard",sections.get(0).getItems().get(0).getHref());
    }

    @Test void doesNotDisplayExternalOrEncodedTraversalMenuUrls() {
        MenuItem external=item(15L,"EXTERNAL_TOOL","External Tool","https://example.com",null,"MONITORING_VIEW");
        MenuItem encoded=item(16L,"ENCODED_TOOL","Encoded Tool","/%2e%2e/admin",null,"MONITORING_VIEW");
        when(repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(external,encoded));
        assertEquals(0,service.getVisibleSections(auth("MONITORING_VIEW")).size());
    }

    private UsernamePasswordAuthenticationToken auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken("operator","n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private MenuItem item(Long id,String key,String name,String url,Long parentId,String permission) {
        MenuItem item=new MenuItem();
        item.setId(id); item.setMenuKey(key); item.setMenuName(name); item.setUrl(url);
        item.setParentId(parentId); item.setIcon("bi-activity"); item.setDisplayOrder(id.intValue());
        item.setRequiredPermission(permission); item.setEnabled(true);
        return item;
    }
}
