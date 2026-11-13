package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.AlertCommentService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Controller
@RequestMapping("/alerts")
public class AlertCommentController {
    private final AlertRepository alertRepository;
    private final AlertCommentService commentService;
    private final DashboardAccessService accessService;

    public AlertCommentController(AlertRepository alertRepository, AlertCommentService commentService,
            DashboardAccessService accessService) {
        this.alertRepository = alertRepository;
        this.commentService = commentService;
        this.accessService = accessService;
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public String addComment(@PathVariable Long id, @RequestParam String comment,
            @RequestParam(defaultValue = "active") String from, Authentication authentication, RedirectAttributes redirect) {
        List<Long> allowedApplicationIds = accessService.getApplicationsWithPermission("ALERT_VIEW")
                .stream().map(Application::getId).toList();
        Alert alert = allowedApplicationIds.isEmpty() ? null
                : alertRepository.findByIdAndMonitoringJob_Application_IdIn(id, allowedApplicationIds).orElse(null);
        if (alert == null) {
            redirect.addFlashAttribute("errorMessage", "Alert was not found or is not accessible.");
            return "redirect:/alerts/history";
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            redirect.addFlashAttribute("errorMessage", "Your signed-in user could not be identified.");
            return "redirect:/alerts/" + id + "?from=" + safeSource(from) + "#alert-comments";
        }
        try {
            commentService.addComment(id, user.getUserId(), comment);
            redirect.addFlashAttribute("successMessage", "Comment added.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/alerts/" + id + "?from=" + safeSource(from);
    }
    private String safeSource(String from) {
        return "history".equalsIgnoreCase(from) ? "history" : "active";
    }

}
