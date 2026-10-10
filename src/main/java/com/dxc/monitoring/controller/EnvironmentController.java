package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.ApplicationRepository;
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

    public EnvironmentController(EnvironmentRepository environmentRepository, ApplicationRepository applicationRepository)
    {
        this.environmentRepository = environmentRepository;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String list(Model model)
    {
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "name"));

        Page<Environment> page = environmentRepository.findAllForList("", pageable);

        model.addAttribute("page", page);
        model.addAttribute("pageSize", 5);
        model.addAttribute("search", "");
        model.addAttribute("currentPage", "environments");
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());

        return "administration/environments";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String table(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<Environment> environmentPage = environmentRepository.findAllForList(search.trim(), pageable);

        model.addAttribute("page", environmentPage);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", search);

        return "administration/environments :: environmentsTable";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String createForm(Model model)
    {
        model.addAttribute("environment", new Environment());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
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

        model.addAttribute("environment", environment);
        model.addAttribute("pageTitle", "Edit Environment");
        model.addAttribute("currentPage", "environments");

        return "administration/environment-form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String save(@ModelAttribute Environment environment, @RequestParam(required = false) Long applicationId,
            RedirectAttributes redirectAttributes)
    {
        if (environment.getId() != null)
        {
            Environment existing = environmentRepository.findById(environment.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environment.getId()));

            existing.setName(environment.getName());
            existing.setDescription(environment.getDescription());
            existing.setEnabled(environment.isEnabled());

            environmentRepository.save(existing);

        } else
        {
            if (applicationId == null)
                throw new IllegalArgumentException("Select an application for this environment.");
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
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));

        environment.setEnabled(!environment.isEnabled());

        environmentRepository.save(environment);

        return "redirect:/administration/environments";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));

        String environmentName = environment.getName();

        environmentRepository.delete(environment);

        redirectAttributes.addFlashAttribute("successMessage",
                "Environment '" + environmentName + "' was deleted successfully.");

        return "redirect:/administration/environments";
    }
}