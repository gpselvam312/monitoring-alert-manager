package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.service.dashboard.DashboardConfigurationService;
import com.dxc.monitoring.service.dashboard.DashboardWidgetForm;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/administration/dashboard")
@PreAuthorize("hasAuthority('MONITORING_CONFIG')")
public class DashboardConfigurationController
{
    private final DashboardConfigurationService dashboardConfigurationService;

    public DashboardConfigurationController(DashboardConfigurationService dashboardConfigurationService)
    {
        this.dashboardConfigurationService = dashboardConfigurationService;
    }

    @GetMapping
    public String dashboardConfiguration(@RequestParam(required = false) Long tabId,
            @RequestParam(defaultValue = "") String tabSearch, @RequestParam(defaultValue = "") String widgetSearch,
            @RequestParam(defaultValue = "0") int tabPage, @RequestParam(defaultValue = "5") int tabSize,
            @RequestParam(defaultValue = "0") int widgetPage, @RequestParam(defaultValue = "5") int widgetSize,
            Model model)
    {
        populateDashboardConfigurationModel(model, tabId, tabSearch, widgetSearch, tabPage, tabSize, widgetPage,
                widgetSize);

        return "administration/dashboard-configuration";
    }

    @PostMapping("/tabs/save")
    public String saveTab(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam Long applicationId, @RequestParam Long environmentId, @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(defaultValue = "true") Boolean enabled, RedirectAttributes redirectAttributes)
    {
        DashboardTab tab;

        if (id == null)
        {
            tab = new DashboardTab();
        } else
        {
            tab = dashboardConfigurationService.findTabById(id);
        }

        tab.setName(name.trim());
        tab.setApplication(dashboardConfigurationService.findApplicationById(applicationId));
        tab.setEnvironment(dashboardConfigurationService.findEnvironmentById(environmentId));
        tab.setSortOrder(sortOrder);
        tab.setEnabled(enabled);

        dashboardConfigurationService.saveTab(tab);

        redirectAttributes.addFlashAttribute("successMessage",
                id == null ? "Dashboard tab created successfully." : "Dashboard tab updated successfully.");

        return "redirect:/administration/dashboard";
    }

    @PostMapping("/tabs/toggle")
    public String toggleTab(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        dashboardConfigurationService.toggleTabEnabled(id);

        redirectAttributes.addFlashAttribute("successMessage", "Dashboard tab status updated successfully.");

        return "redirect:/administration/dashboard";
    }

    @PostMapping("/tabs/delete")
    public String deleteTab(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        String tabName = dashboardConfigurationService.deleteTab(id);

        redirectAttributes.addFlashAttribute("successMessage", "Dashboard tab '" + tabName + "' deleted successfully.");

        return "redirect:/administration/dashboard";
    }

    @PostMapping("/widgets/save")
    public String saveWidget(@Valid @ModelAttribute("dashboardWidgetForm") DashboardWidgetForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes, Model model)
    {
        if (bindingResult.hasErrors())
        {
            populateDashboardConfigurationModel(model, form.getTabId(), "", "", 0, 5, 0, 5);

            model.addAttribute("dashboardWidgetValidationFailed", true);

            return "administration/dashboard-configuration";
        }

        boolean creating = form.getId() == null;

        try
        {
            dashboardConfigurationService.saveWidget(form);
        }
        catch (IllegalArgumentException exception)
        {
            populateDashboardConfigurationModel(model, form.getTabId(), "", "", 0, 5, 0, 5);
            model.addAttribute("dashboardWidgetSaveError", exception.getMessage());
            model.addAttribute("dashboardWidgetValidationFailed", true);
            return "administration/dashboard-configuration";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                creating ? "Dashboard widget created successfully." : "Dashboard widget updated successfully.");

        return "redirect:/administration/dashboard";
    }

    @PostMapping("/widgets/toggle")
    public String toggleWidget(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        dashboardConfigurationService.toggleWidgetEnabled(id);

        redirectAttributes.addFlashAttribute("successMessage", "Dashboard widget status updated successfully.");

        return "redirect:/administration/dashboard";
    }

    @PostMapping("/widgets/delete")
    public String deleteWidget(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        String widgetName = dashboardConfigurationService.deleteWidget(id);

        redirectAttributes.addFlashAttribute("successMessage",
                "Dashboard widget '" + widgetName + "' deleted successfully.");

        return "redirect:/administration/dashboard";
    }

    @GetMapping("/tabs/widgets")
    @ResponseBody
    public List<DashboardWidget> getWidgets(@RequestParam Long tabId)
    {
        return dashboardConfigurationService.findWidgetsByTabId(tabId);
    }

    private int normalizePageSize(int size)
    {
        if (size == 5 || size == 10 || size == 25)
        {
            return size;
        }

        return 10;
    }

    @GetMapping("/tabs/table")
    public String tabsTable(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "sortOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        int pageSize = normalizePageSize(size);

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String sortField = normalizeTabSort(sort);

        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(sortDirection, sortField));

        Page<DashboardTab> tabs = dashboardConfigurationService.findTabs(search, pageable);

        model.addAttribute("tabs", tabs);
        model.addAttribute("tabSearch", search);
        model.addAttribute("pageSize", pageSize);

        return "administration/dashboard-configuration :: dashboardTabsTable";
    }

    @GetMapping("/widgets/table")
    public String widgetsTable(@RequestParam Long tabId, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "sortOrder") String sort, @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        int pageSize = normalizePageSize(size);

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String sortField = normalizeWidgetSort(sort);

        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(sortDirection, sortField));

        DashboardTab selectedTab = dashboardConfigurationService.findTabById(tabId);

        if (selectedTab == null)
        {
            throw new IllegalArgumentException("Dashboard tab not found.");
        }

        Page<DashboardWidget> widgets = dashboardConfigurationService.findWidgetsByTabId(tabId, search, pageable);

        model.addAttribute("selectedTab", selectedTab);
        model.addAttribute("widgets", widgets);
        model.addAttribute("widgetSearch", search);
        model.addAttribute("pageSize", pageSize);

        return "administration/dashboard-configuration :: dashboardWidgetsTable";
    }

    private String normalizeTabSort(String sort)
    {
        return switch (sort)
        {
            case "name" -> "name";
            case "application.name" -> "application.name";
            case "environment.name" -> "environment.name";
            case "sortOrder" -> "sortOrder";
            case "enabled" -> "enabled";
            default -> "sortOrder";
        };
    }

    private String normalizeWidgetSort(String sort)
    {
        return switch (sort)
        {
            case "name" -> "name";
            case "widgetType" -> "widgetType";
            case "size" -> "size";
            case "sortOrder" -> "sortOrder";
            case "enabled" -> "enabled";
            case "autoRefresh" -> "autoRefresh";
            case "detailsEnabled" -> "detailsEnabled";
            default -> "sortOrder";
        };
    }

    @GetMapping("/widgets/{id}")
    @ResponseBody
    public DashboardWidgetForm getWidget(@PathVariable Long id)
    {
        return dashboardConfigurationService.getWidgetForm(id);
    }

    private void populateDashboardConfigurationModel(Model model, Long tabId, String tabSearch, String widgetSearch,
            int tabPage, int tabSize, int widgetPage, int widgetSize)
    {
        Pageable tabPageable = PageRequest.of(Math.max(tabPage, 0), normalizePageSize(tabSize),
                Sort.by("sortOrder").ascending().and(Sort.by("name").ascending()));

        Page<DashboardTab> tabs = dashboardConfigurationService.findTabs(tabSearch, tabPageable);

        List<DashboardTab> dashboardTabs =
            dashboardConfigurationService.findAllTabs().stream().filter(DashboardTab::getEnabled).toList();

        DashboardTab selectedTab = null;

        if (tabId != null)
        {
            selectedTab = dashboardConfigurationService.findTabById(tabId);
        } else if (!tabs.isEmpty())
        {
            selectedTab = tabs.getContent().get(0);
        }

        Page<DashboardWidget> widgets = Page.empty();

        if (selectedTab != null)
        {
            Pageable widgetPageable = PageRequest.of(Math.max(widgetPage, 0), normalizePageSize(widgetSize),
                    Sort.by("sortOrder").ascending().and(Sort.by("name").ascending()));

            widgets =
                dashboardConfigurationService.findWidgetsByTabId(selectedTab.getId(), widgetSearch, widgetPageable);
        }

        model.addAttribute("currentPage", "dashboard-configuration");
        model.addAttribute("tabs", tabs);
        model.addAttribute("dashboardTabs", dashboardTabs);
        model.addAttribute("environments", dashboardConfigurationService.findEnabledEnvironments());
        model.addAttribute("applications", dashboardConfigurationService.findEnabledApplications());
        model.addAttribute("selectedTab", selectedTab);
        model.addAttribute("widgets", widgets);
        model.addAttribute("monitoringJobs", dashboardConfigurationService.findDashboardMonitoringJobs());
        model.addAttribute("tabSearch", tabSearch);
        model.addAttribute("widgetSearch", widgetSearch);
        model.addAttribute("widgetPage", widgetPage);
        int normalizedWidgetSize = normalizePageSize(widgetSize);
        model.addAttribute("widgetSize", normalizedWidgetSize);
        model.addAttribute("pageSize", normalizedWidgetSize);
        
        if (!model.containsAttribute("dashboardWidgetForm"))
        {
            model.addAttribute("dashboardWidgetForm", new DashboardWidgetForm());
        }
    }

}