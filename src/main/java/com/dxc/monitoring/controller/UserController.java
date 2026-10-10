package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/administration/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {
    private final UserService userService;
    private final ApplicationRepository applicationRepository;

    public UserController(UserService userService, ApplicationRepository applicationRepository) {
        this.userService = userService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping
    public String listUsers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "username") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateUserTable(model, page, size, search, sort, direction);
        return "administration/users";
    }

    @GetMapping("/table")
    public String userTable(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "username") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateUserTable(model, page, size, search, sort, direction);
        return "administration/users :: usersTable";
    }

    private void populateUserTable(Model model, int page, int size, String search, String sort, String direction) {
        int safeSize = (size == 5 || size == 10 || size == 25) ? size : 5;
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Comparator<User> comparator = switch (sort == null ? "username" : sort) {
            case "fullName" -> Comparator.comparing(u -> safe(u.getFullName()), String.CASE_INSENSITIVE_ORDER);
            case "email" -> Comparator.comparing(u -> safe(u.getEmail()), String.CASE_INSENSITIVE_ORDER);
            case "enabled" -> Comparator.comparing(User::isEnabled);
            case "createdAt" -> Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(u -> safe(u.getUsername()), String.CASE_INSENSITIVE_ORDER);
        };
        if ("desc".equalsIgnoreCase(direction)) comparator = comparator.reversed();
        List<User> filtered = userService.findAll().stream()
                .filter(u -> query.isBlank() || safe(u.getUsername()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(u.getFullName()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(u.getEmail()).toLowerCase(Locale.ROOT).contains(query)
                        || u.getRoles().stream().map(Role::getName).anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(query)))
                .sorted(comparator).toList();
        int totalPages = (int) Math.ceil((double) filtered.size() / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<User> users = new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
        Map<Long, String> roleSummaries = new HashMap<>();
        users.getContent().forEach(user -> {
            String summary = userService.findRoleAssignmentSummary(user.getId());
            if (!summary.isBlank()) roleSummaries.put(user.getId(), summary);
        });
        List<Long> platformAdminIds = users.getContent().stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName())))
                .map(User::getId).toList();
        model.addAttribute("users", users);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("search", search == null ? "" : search.trim());
        model.addAttribute("roleSummaries", roleSummaries);
        model.addAttribute("platformAdminIds", platformAdminIds);
        model.addAttribute("currentPage", "users");
    }

    private String safe(String value) { return value == null ? "" : value; }

    @GetMapping("/new")
    public String createUserForm(Model model) {
        User user = new User();
        user.setEnabled(true);
        model.addAttribute("user", user);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("selectedApplicationIds", List.of());
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
            @RequestParam(defaultValue = "false") boolean platformAdmin) {
        userService.create(user, password, parseRoleAssignments(applicationIds, allParams), platformAdmin);
        return "redirect:/administration/users";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        model.addAttribute("user", user);
        model.addAttribute("platformAdmin", user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName())));
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
            @RequestParam(defaultValue = "false") boolean platformAdmin) {
        userService.update(id, user.getFullName(), user.getEmail(), user.isEnabled(), password,
                parseRoleAssignments(applicationIds, allParams), platformAdmin);
        return "redirect:/administration/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggleUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user.getRoles().stream().noneMatch(role -> "ADMIN".equals(role.getName()))) {
            userService.toggleEnabled(id);
        }
        return "redirect:/administration/users";
    }

    private Map<Long, Long> parseRoleAssignments(List<Long> applicationIds, Map<String, String> params) {
        Map<Long, Long> assignments = new HashMap<>();
        if (applicationIds == null) return assignments;
        for (Long applicationId : applicationIds.stream().distinct().toList()) {
            String roleValue = params.get("role_" + applicationId);
            if (roleValue == null || roleValue.isBlank())
                throw new IllegalArgumentException("Select a role for every assigned application.");
            try {
                assignments.put(applicationId, Long.parseLong(roleValue));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid role selected for application " + applicationId + ".");
            }
        }
        return assignments;
    }
}