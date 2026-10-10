package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.entity.Application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/administration/environments")
public class EnvironmentController
{
    private final EnvironmentRepository environmentRepository;
    private final ApplicationRepository applicationRepository;
    private final DashboardAccessService accessService;

    public EnvironmentController(EnvironmentRepository environmentRepository, ApplicationRepository applicationRepository,
            DashboardAccessService accessService)
    {
        this.environmentRepository = environmentRepository;
        this.applicationRepository = applicationRepository;
        this.accessService = accessService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String list(Model model)
    {
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "name"));

        java.util.List<Long> applicationIds = accessService.getApplicationsWithPermission("SYSTEM_CONFIG").stream()
                .map(Application::getId).toList();
        Page<Environment> page = applicationIds.isEmpty() ? Page.empty(pageable)
                : environmentRepository.findAllForApplications("", applicationIds, pageable);

        model.addAttribute("page", page);
        model.addAttribute("pageSize", 5);
        model.addAttribute("search", "");
        model.addAttribute("currentPage", "environments");
        model.addAttribute("applications", accessService.getApplicationsWithPermission("SYSTEM_CONFIG"));

        return "administration/environments";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String table(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        java.util.List<Long> applicationIds = accessService.getApplicationsWithPermission("SYSTEM_CONFIG").stream()
                .map(Application::getId).toList();
        Page<Environment> environmentPage = applicationIds.isEmpty() ? Page.empty(pageable)
                : environmentRepository.findAllForApplications(search.trim(), applicationIds, pageable);

        model.addAttribute("page", environmentPage);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", search);

        return "administration/environments :: environmentsTable";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String createForm(Model model)
    {
        model.addAttribute("environment", new Environment());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("pageTitle", "Add Environment");
        model.addAttribute("currentPage", "environments");

        return "administration/environment-form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String editForm(@PathVariable Long id, Model model)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        accessService.assertCanAccessApplication(environment.getApplication().getId(), "SYSTEM_CONFIG");

        model.addAttribute("environment", environment);
        model.addAttribute("pageTitle", "Edit Environment");
        model.addAttribute("currentPage", "environments");

        return "administration/environment-form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String save(@ModelAttribute Environment environment, @RequestParam(required = false) Long applicationId,
            RedirectAttributes redirectAttributes)
    {
        if (environment.getId() != null)
        {
            Environment existing = environmentRepository.findById(environment.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environment.getId()));
            accessService.assertCanAccessApplication(existing.getApplication().getId(), "SYSTEM_CONFIG");

            existing.setName(environment.getName());
            existing.setDescription(environment.getDescription());
            existing.setEnabled(environment.isEnabled());

            environmentRepository.save(existing);

        } else
        {
            if (applicationId == null)
                throw new IllegalArgumentException("Select an application for this environment.");
            accessService.assertCanAccessApplication(applicationId, "SYSTEM_CONFIG");
            Application application = applicationRepository.findById(applicationId)
                    .filter(Application::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found or disabled: " + applicationId));
            environment.setApplication(application);
            environmentRepository.save(environment);
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Environment '" + environment.getName() + "' was saved successfully.");

        return "redirect:/administration/environments";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        accessService.assertCanAccessApplication(environment.getApplication().getId(), "SYSTEM_CONFIG");

        environment.setEnabled(!environment.isEnabled());

        environmentRepository.save(environment);

        return "redirect:/administration/environments";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        accessService.assertCanAccessApplication(environment.getApplication().getId(), "SYSTEM_CONFIG");

        String environmentName = environment.getName();

        environmentRepository.delete(environment);

        redirectAttributes.addFlashAttribute("successMessage",
                "Environment '" + environmentName + "' was deleted successfully.");

        return "redirect:/administration/environments";
    }
}