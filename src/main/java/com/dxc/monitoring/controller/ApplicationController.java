package com.dxc.monitoring.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Controller
@RequestMapping("/administration/applications")
public class ApplicationController
{
    private final ApplicationRepository applicationRepository;
    private final MonitoringJobRepository monitoringJobRepository;
    private final DashboardAccessService accessService;

    private static final Map<String, String> APPLICATION_SORT_FIELDS =
        Map.of("name", "name", "description", "description", "enabled", "enabled");

    public ApplicationController(ApplicationRepository applicationRepository,
            MonitoringJobRepository monitoringJobRepository, DashboardAccessService accessService)
    {
        this.applicationRepository = applicationRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.accessService = accessService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Sort pageableSort = buildApplicationSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        java.util.List<Long> accessibleIds = accessService.getAccessibleApplications().stream()
                .map(Application::getId).toList();
        Page<Application> applications = accessibleIds.isEmpty() ? Page.empty(pageable)
                : applicationRepository.findAllForApplications(normalizedSearch, accessibleIds, pageable);

        model.addAttribute("applications", applications);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("currentPage", "applications");

        return "administration/applications";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String applicationTable(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "name") String sort, @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Sort pageableSort = buildApplicationSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        Page<Application> applications = applicationRepository.findAllForList(normalizedSearch, pageable);

        model.addAttribute("applications", applications);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);

        return "administration/applications :: applicationsTable";
    }

    private Sort buildApplicationSort(String sort, String direction)
    {
        String sortField = APPLICATION_SORT_FIELDS.getOrDefault(sort, "name");

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(sortDirection, sortField);
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String createForm(Model model)
    {
        model.addAttribute("appName", new Application());
        model.addAttribute("pageTitle", "Add Application");
        model.addAttribute("currentPage", "applications");

        return "administration/application-form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String editForm(@PathVariable Long id, Model model)
    {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));

        model.addAttribute("appName", application);
        model.addAttribute("pageTitle", "Edit Application");
        model.addAttribute("currentPage", "applications");

        return "administration/application-form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String save(@ModelAttribute("appName") Application application)
    {
        if (application.getId() != null)
        {
            Application existing = applicationRepository.findById(application.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + application.getId()));

            existing.setName(application.getName());
            existing.setDescription(application.getDescription());
            existing.setEnabled(application.isEnabled());

            applicationRepository.save(existing);
        } else
        {
            applicationRepository.save(application);
        }

        return "redirect:/administration/applications";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String toggle(@PathVariable Long id)
    {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));

        application.setEnabled(!application.isEnabled());

        applicationRepository.save(application);

        return "redirect:/administration/applications";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));

        if (monitoringJobRepository.existsByApplicationId(id))
        {
            redirectAttributes.addFlashAttribute("errorMessage", "Application '" + application.getName()
                    + "' cannot be deleted because it is referenced by monitoring jobs.");

            return "redirect:/administration/applications";
        }

        applicationRepository.delete(application);

        redirectAttributes.addFlashAttribute("successMessage",
                "Application '" + application.getName() + "' was deleted successfully.");

        return "redirect:/administration/applications";
    }
}