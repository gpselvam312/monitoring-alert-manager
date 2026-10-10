package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/administration/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController
{
    private final UserService userService;
    private final ApplicationRepository applicationRepository;

    public UserController(UserService userService, ApplicationRepository applicationRepository)
    {
        this.userService = userService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping
    public String listUsers(Model model)
    {
        List<User> users = userService.findAll();
        Map<Long, String> roleSummaries = new HashMap<>();
        users.forEach(user -> {
            String summary = userService.findRoleAssignmentSummary(user.getId());
            if (!summary.isBlank()) roleSummaries.put(user.getId(), summary);
        });
        model.addAttribute("users", users);
        model.addAttribute("roleSummaries", roleSummaries);
        model.addAttribute("currentPage", "users");

        return "administration/users";
    }

    @GetMapping("/new")
    public String createUserForm(Model model)
    {
        User user = new User();
        user.setEnabled(true);

        model.addAttribute("user", user);
        model.addAttribute("platformAdmin", user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName())));
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("selectedApplicationIds", java.util.List.of());
        model.addAttribute("roleAssignments", Map.of());
        model.addAttribute("platformAdmin", false);
        model.addAttribute("pageTitle", "Add User");
        model.addAttribute("currentPage", "users");

        return "administration/user-form";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute("user") User user, @RequestParam("password") String password,
            @RequestParam(name = "applicationIds", required = false) List<Long> applicationIds,
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "false") boolean platformAdmin)
    {
        userService.create(user, password, parseRoleAssignments(applicationIds, allParams), platformAdmin);

        return "redirect:/administration/users";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model)
    {
        User user = userService.findById(id);

        model.addAttribute("user", user);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        Map<Long, Long> roleAssignments = userService.findRoleAssignments(id);
        model.addAttribute("roleAssignments", roleAssignments);
        model.addAttribute("selectedApplicationIds", roleAssignments.keySet());
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("currentPage", "users");

        return "administration/user-form";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Long id, @ModelAttribute("user") User user,
            @RequestParam("password") String password,
            @RequestParam(name = "applicationIds", required = false) List<Long> applicationIds,
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "false") boolean platformAdmin)
    {
        userService.update(id, user.getFullName(), user.getEmail(), user.isEnabled(), password,
                parseRoleAssignments(applicationIds, allParams), platformAdmin);

        return "redirect:/administration/users";
    }

    private Map<Long, Long> parseRoleAssignments(List<Long> applicationIds, Map<String, String> params) {
        Map<Long, Long> assignments = new HashMap<>();
        if (applicationIds == null) return assignments;
        for (Long applicationId : applicationIds.stream().distinct().toList()) {
            String roleValue = params.get("role_" + applicationId);
            if (roleValue == null || roleValue.isBlank()) {
                throw new IllegalArgumentException("Select a role for every assigned application.");
            }
            try {
                assignments.put(applicationId, Long.parseLong(roleValue));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid role selected for application " + applicationId + ".");
            }
        }
        return assignments;
    }

    @PostMapping("/{id}/toggle")
    public String toggleUser(@PathVariable Long id)
    {
        userService.toggleEnabled(id);

        return "redirect:/administration/users";
    }

}
