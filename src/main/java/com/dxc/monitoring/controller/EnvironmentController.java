package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.MachineRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;
import com.dxc.monitoring.entity.Application;

import org.springframework.data.domain.Page;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.dxc.monitoring.service.ReferenceDataChangedEvent;
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
    private final MonitoringJobRepository monitoringJobRepository;
    private final DashboardTabRepository dashboardTabRepository;
    private final MachineRepository machineRepository;
    private final PageRequestSupport pageRequestSupport;
    private final ApplicationEventPublisher eventPublisher;

    public EnvironmentController(EnvironmentRepository environmentRepository, ApplicationRepository applicationRepository,
            DashboardAccessService accessService, MonitoringJobRepository monitoringJobRepository,
            DashboardTabRepository dashboardTabRepository, MachineRepository machineRepository,
            PageRequestSupport pageRequestSupport, ApplicationEventPublisher eventPublisher)
    {
        this.environmentRepository = environmentRepository;
        this.applicationRepository = applicationRepository;
        this.accessService = accessService;
        this.monitoringJobRepository = monitoringJobRepository;
        this.dashboardTabRepository = dashboardTabRepository;
        this.machineRepository = machineRepository;
        this.pageRequestSupport = pageRequestSupport;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String list(Model model)
    {
        var spec = pageRequestSupport.create(0, 5, "name", "asc", "name",
                Map.of("name", "name", "description", "description", "enabled", "enabled"));
        java.util.List<Application> applications = accessService.getApplicationsWithPermission("SYSTEM_CONFIG");
        java.util.List<Long> applicationIds = applications.stream().map(Application::getId).toList();
        Page<Environment> page = applicationIds.isEmpty() ? Page.empty(spec.pageable())
                : environmentRepository.findAllForApplications("", applicationIds, spec.pageable());

        model.addAttribute("page", page);
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("search", "");
        model.addAttribute("currentPage", "environments");
        model.addAttribute("applications", applications);

        return "administration/environments";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String table(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        var spec = pageRequestSupport.create(page, size, sort, direction, "name",
                Map.of("name", "name", "description", "description", "enabled", "enabled"));
        java.util.List<Long> applicationIds = accessService.getApplicationsWithPermission("SYSTEM_CONFIG").stream()
                .map(Application::getId).toList();
        Page<Environment> environmentPage = applicationIds.isEmpty() ? Page.empty(spec.pageable())
                : environmentRepository.findAllForApplications(search == null ? "" : search.trim(),
                        applicationIds, spec.pageable());

        model.addAttribute("page", environmentPage);
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("search", search == null ? "" : search.trim());
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());

        return "administration/environments :: environmentsTable";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String createForm(Model model)
    {
        model.addAttribute("environment", new Environment());
        model.addAttribute("applications", accessService.getApplicationsWithPermission("SYSTEM_CONFIG"));
        model.addAttribute("pageTitle", "Add Environment");
        model.addAttribute("currentPage", "environments");

        return "administration/environment-form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
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
    @Transactional
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String save(@ModelAttribute Environment environment, @RequestParam(required = false) Long applicationId,
            RedirectAttributes redirectAttributes)
    {
        if (environment.getName() == null || environment.getName().isBlank())
            throw new IllegalArgumentException("Environment name is required.");
        environment.setName(environment.getName().trim());
        if (environment.getId() != null)
        {
            Environment existing = environmentRepository.findById(environment.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environment.getId()));
            accessService.assertCanAccessApplication(existing.getApplication().getId(), "SYSTEM_CONFIG");

            if (environmentRepository.findByApplicationIdAndNameIgnoreCase(
                    existing.getApplication().getId(), environment.getName())
                    .filter(other -> !other.getId().equals(existing.getId())).isPresent())
                throw new IllegalArgumentException("An environment with this name already exists for the application.");
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
            if (environmentRepository.existsByApplicationIdAndNameIgnoreCase(applicationId, environment.getName()))
                throw new IllegalArgumentException("An environment with this name already exists for the application.");
            environment.setApplication(application);
            environmentRepository.save(environment);
        }

        eventPublisher.publishEvent(new ReferenceDataChangedEvent());
        redirectAttributes.addFlashAttribute("successMessage",
                "Environment '" + environment.getName() + "' was saved successfully.");

        return "redirect:/administration/environments";
    }

    @PostMapping("/{id}/toggle")
    @Transactional
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        accessService.assertCanAccessApplication(environment.getApplication().getId(), "SYSTEM_CONFIG");

        environment.setEnabled(!environment.isEnabled());

        environmentRepository.save(environment);
        eventPublisher.publishEvent(new ReferenceDataChangedEvent());
        return "redirect:/administration/environments";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        accessService.assertCanAccessApplication(environment.getApplication().getId(), "SYSTEM_CONFIG");

        String environmentName = environment.getName();
        if (monitoringJobRepository.existsByEnvironmentId(id)
                || dashboardTabRepository.existsByEnvironment_Id(id)
                || machineRepository.existsByEnvironment_Id(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Environment '" + environmentName
                    + "' cannot be deleted while monitoring jobs, dashboard tabs, or machines reference it.");
            return "redirect:/administration/environments";
        }

        environmentRepository.delete(environment);
        eventPublisher.publishEvent(new ReferenceDataChangedEvent());
        redirectAttributes.addFlashAttribute("successMessage",
                "Environment '" + environmentName + "' was deleted successfully.");

        return "redirect:/administration/environments";
    }
}