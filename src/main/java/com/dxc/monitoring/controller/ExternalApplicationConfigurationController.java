package com.dxc.monitoring.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.ExternalApplication;
import com.dxc.monitoring.repository.ExternalApplicationTypeRepository;
import com.dxc.monitoring.service.external.ExternalApplicationForm;
import com.dxc.monitoring.service.external.ExternalApplicationService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/administration/external-applications")
@PreAuthorize("hasAuthority('MONITORING_CONFIG')")
public class ExternalApplicationConfigurationController
{
    private static final List<Integer> PAGE_SIZES = List.of(5, 10, 25);
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "applicationCode", "applicationCode",
            "applicationName", "applicationName",
            "baseUrl", "baseUrl",
            "authType", "authType",
            "externalType", "externalType",
            "enabled", "enabled");

    private final ExternalApplicationService externalApplicationService;
    private final ExternalApplicationTypeRepository externalApplicationTypeRepository;
    private final DashboardAccessService accessService;
    private final PageRequestSupport pageRequestSupport;

    public ExternalApplicationConfigurationController(ExternalApplicationService externalApplicationService,
            ApplicationRepository applicationRepository, EnvironmentRepository environmentRepository,
            ExternalApplicationTypeRepository externalApplicationTypeRepository,
            DashboardAccessService accessService, PageRequestSupport pageRequestSupport)
    {
        this.externalApplicationService = externalApplicationService;
        this.externalApplicationTypeRepository = externalApplicationTypeRepository;
        this.accessService = accessService;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "applicationName") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "") String externalType,
            Model model)
    {
        populateModel(model, id, page, size, search, sort, direction, applicationId, environmentId, externalType);
        return "administration/external-applications";
    }

    @GetMapping("/table")
    public String table(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "applicationName") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "") String externalType,
            Model model)
    {
        populateTableModel(model, page, size, search, sort, direction, applicationId, environmentId, externalType);
        return "administration/external-applications :: externalApplicationsTable";
    }

    @PostMapping("/save")
    public String save(ExternalApplicationForm form, Model model, RedirectAttributes redirectAttributes)
    {
        boolean updating = form.getId() != null;
        try
        {
            externalApplicationService.save(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    updating ? "External application updated successfully."
                            : "External application saved successfully.");
            return "redirect:/administration/external-applications";
        }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            populateModel(model, form.getId(), 0, 5, "", "applicationName", "asc", null, null, "");
            // Preserve non-secret form fields on validation errors. Never echo the submitted secret.
            ExternalApplicationForm safeForm = externalApplicationService.getForm(form.getId());
            safeForm.setApplicationCode(form.getApplicationCode());
            safeForm.setApplicationName(form.getApplicationName());
            safeForm.setExternalType(form.getExternalType());
            safeForm.setApplicationId(form.getApplicationId());
            safeForm.setEnvironmentId(form.getEnvironmentId());
            safeForm.setBaseUrl(form.getBaseUrl());
            safeForm.setAuthType(form.getAuthType());
            safeForm.setEnabled(form.isEnabled());
            safeForm.setAccountName(form.getAccountName());
            safeForm.setUsername(form.getUsername());
            safeForm.setSecretReference(form.getSecretReference());
            safeForm.setAccountEnabled(form.isAccountEnabled());
            safeForm.setClearSecret(form.isClearSecret());
            model.addAttribute("externalApplicationForm", safeForm);
            model.addAttribute("externalApplicationError", exception.getMessage());
            return "administration/external-applications";
        }
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        try
        {
            externalApplicationService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "External application deleted successfully.");
        }
        catch (IllegalArgumentException exception)
        {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/administration/external-applications";
    }

    @PostMapping("/toggle")
    public String toggle(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        externalApplicationService.toggleEnabled(id);
        redirectAttributes.addFlashAttribute("successMessage", "External application status updated.");
        return "redirect:/administration/external-applications";
    }

    private void populateModel(Model model, Long id, int page, int size, String search, String sort, String direction,
            Long applicationId, Long environmentId, String externalType)
    {
        populateTableModel(model, page, size, search, sort, direction, applicationId, environmentId, externalType);
        model.addAttribute("currentPage", "external-applications");
        if (!model.containsAttribute("externalApplicationForm"))
            model.addAttribute("externalApplicationForm", externalApplicationService.getForm(id));
    }

    private void populateTableModel(Model model, int page, int size, String search, String sort, String direction,
            Long applicationId, Long environmentId, String externalType)
    {
        var spec = pageRequestSupport.create(page, size, sort, direction, "applicationName", SORT_FIELDS);
        int pageSize = spec.pageSize();
        int requestedPage = spec.pageable().getPageNumber();
        String normalizedSearch = search == null ? "" : search.trim();
        Pageable pageable = spec.pageable();
        Page<ExternalApplication> applications = externalApplicationService.findPage(
                normalizedSearch, applicationId, environmentId, externalType, pageable);

        if (applications.getTotalPages() > 0 && requestedPage >= applications.getTotalPages())
        {
            pageable = pageRequestSupport.create(applications.getTotalPages() - 1, pageSize,
                    spec.sort(), spec.direction(), "applicationName", SORT_FIELDS).pageable();
            applications = externalApplicationService.findPage(
                    normalizedSearch, applicationId, environmentId, externalType, pageable);
        }

        List<Long> externalApplicationIds = applications.getContent().stream().map(ExternalApplication::getId).toList();
        java.util.Set<Long> configuredAccountIds = externalApplicationService.findConfiguredAccountIds(externalApplicationIds);
        Map<Long, Boolean> configuredAccounts = new HashMap<>();
        externalApplicationIds.forEach(id -> configuredAccounts.put(id, configuredAccountIds.contains(id)));

        model.addAttribute("externalApplications", applications);
        model.addAttribute("configuredAccounts", configuredAccounts);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("selectedApplicationId", applicationId);
        model.addAttribute("selectedEnvironmentId", environmentId);
        model.addAttribute("selectedExternalType", externalType == null ? "" : externalType);
        model.addAttribute("externalApplicationTypes", externalApplicationService.findEnabledTypes());
        model.addAttribute("externalTypeLabels", externalApplicationTypeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(type -> type.getCode(), type -> type.getDisplayName())));
        List<com.dxc.monitoring.entity.Application> accessibleApplications =
                accessService.getApplicationsWithPermission("MONITORING_CONFIG");
        model.addAttribute("internalApplications", accessibleApplications);
        List<com.dxc.monitoring.entity.Environment> accessibleEnvironments = accessibleApplications.stream()
                .flatMap(application -> accessService.getEnvironmentsForApplication(
                        application.getId(), "MONITORING_CONFIG").stream()).toList();
        model.addAttribute("environments", accessibleEnvironments);
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());
        model.addAttribute("currentPage", "external-applications");
    }
}