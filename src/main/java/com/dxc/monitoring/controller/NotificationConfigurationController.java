package com.dxc.monitoring.controller;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRoute;
import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.repository.NotificationChannelRepository;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.repository.NotificationGroupRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.repository.NotificationRecipientRepository;
import com.dxc.monitoring.repository.NotificationRouteRepository;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.NotificationConfigurationService;
import com.dxc.monitoring.web.NotificationChannelForm;

@Controller
@RequestMapping("/administration/notifications")
public class NotificationConfigurationController {
    private final NotificationChannelRepository channelRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationGroupRepository groupRepository;
    private final NotificationPolicyRepository policyRepository;
    private final NotificationRouteRepository routeRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationConfigurationService configurationService;

    public NotificationConfigurationController(NotificationChannelRepository channelRepository,
            NotificationRecipientRepository recipientRepository, NotificationGroupRepository groupRepository,
            NotificationPolicyRepository policyRepository, NotificationRouteRepository routeRepository,
            NotificationDeliveryRepository deliveryRepository, NotificationConfigurationService configurationService) {
        this.channelRepository = channelRepository;
        this.recipientRepository = recipientRepository;
        this.groupRepository = groupRepository;
        this.policyRepository = policyRepository;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.configurationService = configurationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('NOTIFICATION_VIEW', 'NOTIFICATION_CONFIG')")
    public String index(Model model) {
        model.addAttribute("channels", channelRepository.findAllByOrderByNameAsc());
        model.addAttribute("recipients", recipientRepository.findAll());
        model.addAttribute("groups", groupRepository.findAllByOrderByNameAsc());
        model.addAttribute("policies", policyRepository.findAllByOrderByNameAsc());
        model.addAttribute("routes", routeRepository.findAllByOrderByIdAsc());
        Page<NotificationDelivery> deliveries = deliveryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20));
        model.addAttribute("deliveries", deliveries);
        model.addAttribute("channelForm", new NotificationChannelForm());
        model.addAttribute("channelTypes", NotificationChannel.ChannelType.values());
        model.addAttribute("severities", AlertRule.Severity.values());
        model.addAttribute("eventTypes", NotificationRoute.EventType.values());
        model.addAttribute("currentPage", "notification-configuration");
        return "administration/notifications";
    }

    @PostMapping("/channels")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String createChannel(@ModelAttribute NotificationChannelForm form, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            configurationService.createChannel(form, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Notification channel created. Secret values are encrypted at rest.");
        } catch (IllegalArgumentException | DataIntegrityViolationException | IllegalStateException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    @PostMapping("/recipients")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String createRecipient(@RequestParam String name, @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            configurationService.createRecipient(name, email, phoneNumber, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Notification recipient created.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String createGroup(@RequestParam String name, @RequestParam(required = false) String description,
            @RequestParam(required = false) List<Long> recipientIds, Authentication authentication,
            RedirectAttributes redirect) {
        try {
            configurationService.createGroup(name, description, recipientIds, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Notification group created.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String createPolicy(@RequestParam String name, @RequestParam(required = false) String description,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            configurationService.createPolicy(name, description, userId(authentication));
            redirect.addFlashAttribute("successMessage", "Notification policy created.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    @PostMapping("/routes")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String createRoute(@RequestParam Long policyId, @RequestParam Long channelId,
            @RequestParam String severity, @RequestParam String eventType, @RequestParam String targetType,
            @RequestParam(required = false) Long recipientId, @RequestParam(required = false) Long groupId,
            @RequestParam(defaultValue = "0") int minimumIntervalSeconds, RedirectAttributes redirect) {
        try {
            configurationService.createRoute(policyId, channelId, severity, eventType, targetType,
                    recipientId, groupId, minimumIntervalSeconds);
            redirect.addFlashAttribute("successMessage", "Notification route created.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    @PostMapping("/channels/{id}/toggle")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String toggleChannel(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        return toggle(() -> configurationService.toggleChannel(id, userId(authentication)), "Notification channel updated.", redirect);
    }

    @PostMapping("/recipients/{id}/toggle")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String toggleRecipient(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        return toggle(() -> configurationService.toggleRecipient(id, userId(authentication)), "Recipient updated.", redirect);
    }

    @PostMapping("/groups/{id}/toggle")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String toggleGroup(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        return toggle(() -> configurationService.toggleGroup(id, userId(authentication)), "Notification group updated.", redirect);
    }

    @PostMapping("/policies/{id}/toggle")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String togglePolicy(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        return toggle(() -> configurationService.togglePolicy(id, userId(authentication)), "Notification policy updated.", redirect);
    }

    @PostMapping("/routes/{id}/toggle")
    @PreAuthorize("hasAuthority('NOTIFICATION_CONFIG')")
    public String toggleRoute(@PathVariable Long id, RedirectAttributes redirect) {
        return toggle(() -> configurationService.toggleRoute(id), "Notification route updated.", redirect);
    }

    private String toggle(Runnable action, String success, RedirectAttributes redirect) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", safeMessage(exception));
        }
        return "redirect:/administration/notifications";
    }

    private Long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (exception instanceof DataIntegrityViolationException) {
            return "This value conflicts with existing configuration. Check names, channel secrets and route selections.";
        }
        if (message == null || message.isBlank()) return "Notification configuration could not be saved. Check the supplied values.";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
