package com.dxc.monitoring.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ExternalApplicationService externalApplicationService;

    public ExternalApplicationConfigurationController(ExternalApplicationService externalApplicationService)
    {
        this.externalApplicationService = externalApplicationService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long id, Model model)
    {
        populateModel(model, id);
        return "administration/external-applications";
    }

    @PostMapping("/save")
    public String save(ExternalApplicationForm form, Model model, RedirectAttributes redirectAttributes)
    {
        try
        {
            externalApplicationService.save(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    form.getId() == null ? "External application saved successfully."
                            : "External application updated successfully.");
            return "redirect:/administration/external-applications";
        }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            populateModel(model, form.getId());
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

    private void populateModel(Model model, Long id)
    {
        List<ExternalApplication> applications = externalApplicationService.findAll();
        Map<Long, Boolean> configuredAccounts = new HashMap<>();
        for (ExternalApplication application : applications)
            configuredAccounts.put(application.getId(), externalApplicationService.hasAccount(application.getId()));

        model.addAttribute("currentPage", "external-applications");
        model.addAttribute("externalApplications", applications);
        model.addAttribute("configuredAccounts", configuredAccounts);
        if (!model.containsAttribute("externalApplicationForm"))
            model.addAttribute("externalApplicationForm", externalApplicationService.getForm(id));
    }
}