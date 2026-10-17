package com.dxc.monitoring.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.ExternalApplication;
import com.dxc.monitoring.service.external.ExternalApplicationForm;
import com.dxc.monitoring.service.external.ExternalApplicationService;

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
            "enabled", "enabled");

    private final ExternalApplicationService externalApplicationService;

    public ExternalApplicationConfigurationController(ExternalApplicationService externalApplicationService)
    {
        this.externalApplicationService = externalApplicationService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "applicationName") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        populateModel(model, id, page, size, search, sort, direction);
        return "administration/external-applications";
    }

    @GetMapping("/table")
    public String table(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "applicationName") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        populateTableModel(model, page, size, search, sort, direction);
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
            populateModel(model, form.getId(), 0, 5, "", "applicationName", "asc");
            // Preserve non-secret form fields on validation errors. Never echo the submitted secret.
            ExternalApplicationForm safeForm = externalApplicationService.getForm(form.getId());
            safeForm.setApplicationCode(form.getApplicationCode());
            safeForm.setApplicationName(form.getApplicationName());
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

    @PostMapping("/toggle")
    public String toggle(@RequestParam Long id, RedirectAttributes redirectAttributes)
    {
        externalApplicationService.toggleEnabled(id);
        redirectAttributes.addFlashAttribute("successMessage", "External application status updated.");
        return "redirect:/administration/external-applications";
    }

    private void populateModel(Model model, Long id, int page, int size, String search, String sort, String direction)
    {
        populateTableModel(model, page, size, search, sort, direction);
        model.addAttribute("currentPage", "EXTERNAL_APPLICATIONS");
        if (!model.containsAttribute("externalApplicationForm"))
            model.addAttribute("externalApplicationForm", externalApplicationService.getForm(id));
    }

    private void populateTableModel(Model model, int page, int size, String search, String sort, String direction)
    {
        int pageSize = PAGE_SIZES.contains(size) ? size : 5;
        int requestedPage = Math.max(0, page);
        String normalizedSearch = search == null ? "" : search.trim();
        String sortField = SORT_FIELDS.getOrDefault(sort, "applicationName");
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(requestedPage, pageSize, Sort.by(sortDirection, sortField));
        Page<ExternalApplication> applications = externalApplicationService.findPage(normalizedSearch, pageable);

        if (applications.getTotalPages() > 0 && requestedPage >= applications.getTotalPages())
        {
            pageable = PageRequest.of(applications.getTotalPages() - 1, pageSize, Sort.by(sortDirection, sortField));
            applications = externalApplicationService.findPage(normalizedSearch, pageable);
        }

        Map<Long, Boolean> configuredAccounts = new HashMap<>();
        for (ExternalApplication application : applications.getContent())
            configuredAccounts.put(application.getId(), externalApplicationService.hasAccount(application.getId()));

        model.addAttribute("externalApplications", applications);
        model.addAttribute("configuredAccounts", configuredAccounts);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("currentPage", "EXTERNAL_APPLICATIONS");
    }
}
