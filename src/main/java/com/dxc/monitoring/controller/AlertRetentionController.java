package com.dxc.monitoring.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.AlertCleanupRun;
import com.dxc.monitoring.entity.AlertRetentionPolicy;
import com.dxc.monitoring.repository.AlertCleanupRunRepository;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.AlertRetentionCleanupExecutor;
import com.dxc.monitoring.service.AlertRetentionService;

@Controller
@RequestMapping("/administration/alert-retention")
public class AlertRetentionController {
    private final AlertRetentionService retentionService;
    private final AlertCleanupRunRepository runRepository;

    public AlertRetentionController(AlertRetentionService retentionService,
            AlertCleanupRunRepository runRepository) {
        this.retentionService = retentionService;
        this.runRepository = runRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DATA_EXPUNGE')")
    public String index(@RequestParam(required = false) Long previewPolicyId, Model model) {
        model.addAttribute("policies", retentionService.findPolicies());
        Page<AlertCleanupRun> runs = runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 20));
        model.addAttribute("runs", runs);
        model.addAttribute("dataTypes", AlertRetentionPolicy.DataType.values());
        model.addAttribute("currentPage", "alert-retention");
        if (previewPolicyId != null) {
            try {
                AlertRetentionCleanupExecutor.Preview preview = retentionService.preview(previewPolicyId);
                model.addAttribute("preview", preview);
                model.addAttribute("previewPolicyId", previewPolicyId);
            } catch (IllegalArgumentException exception) {
                model.addAttribute("errorMessage", safeMessage(exception));
            }
        }
        return "administration/alert-retention";
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('DATA_EXPUNGE')")
    public String createPolicy(@RequestParam String dataType, @RequestParam int retentionDays,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(required = false) String description, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            retentionService.createPolicy(dataType, retentionDays, enabled, description, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Retention policy saved. Cleanup is never run automatically.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/alert-retention";
    }

    @PostMapping("/policies/{id}/update")
    @PreAuthorize("hasAuthority('DATA_EXPUNGE')")
    public String updatePolicy(@PathVariable Long id, @RequestParam int retentionDays,
            @RequestParam(required = false) String description, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            retentionService.updatePolicy(id, retentionDays, description, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Retention policy updated.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/alert-retention";
    }

    @PostMapping("/policies/{id}/toggle")
    @PreAuthorize("hasAuthority('DATA_EXPUNGE')")
    public String togglePolicy(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            retentionService.togglePolicy(id, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Retention policy status updated.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/alert-retention";
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('DATA_EXPUNGE')")
    public String runCleanup(@RequestParam Long policyId, @RequestParam(defaultValue = "false") boolean confirm,
            Authentication authentication, RedirectAttributes redirect) {
        if (!confirm) {
            redirect.addFlashAttribute("errorMessage", "Confirm the cleanup operation before proceeding.");
            return "redirect:/administration/alert-retention";
        }
        try {
            AlertCleanupRun run = retentionService.runCleanup(policyId, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Cleanup completed. Deleted " + run.getRecordsDeleted()
                    + " records after examining " + run.getRecordsExamined() + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/alert-retention";
    }

    private Long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return "A retention policy already exists for this data type.";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Retention operation could not be completed.";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
