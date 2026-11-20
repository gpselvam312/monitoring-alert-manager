package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.service.UserService;
import com.dxc.monitoring.web.PageRequestSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/administration/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "username", "username", "fullName", "fullName", "email", "email",
            "enabled", "enabled", "createdAt", "createdAt");
    private final UserService userService;
    private final ApplicationRepository applicationRepository;
    private final PageRequestSupport pageRequestSupport;

    public UserController(UserService userService, ApplicationRepository applicationRepository,
            PageRequestSupport pageRequestSupport) {
        this.userService = userService;
        this.applicationRepository = applicationRepository;
        this.pageRequestSupport = pageRequestSupport;
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
        String query = search == null ? "" : search.trim();
        var spec = pageRequestSupport.create(page, size, sort, direction, "username", SORT_FIELDS);
        Page<User> users = userService.findPage(query, spec.pageable());
        List<Long> ids = users.getContent().stream().map(User::getId).toList();
        Map<Long, String> roleSummaries = userService.findRoleAssignmentSummaries(ids);
        List<Long> platformAdminIds = users.getContent().stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName())))
                .map(User::getId).toList();
        model.addAttribute("users", users);
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("search", query);
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());
        model.addAttribute("roleSummaries", roleSummaries);
        model.addAttribute("platformAdminIds", platformAdminIds);
        model.addAttribute("currentPage", "users");
    }

    @GetMapping("/new")
    public String createUserForm(Model model) {
        User user = new User();
        user.setEnabled(true);
        model.addAttribute("user", user);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("selectedApplicationIds", List.of());
        model.addAttribute("roleAssignments", Map.of());
        model.addAttribute("primaryApplicationId", null);
        model.addAttribute("platformAdmin", false);
        model.addAttribute("pageTitle", "Add User");
        model.addAttribute("currentPage", "users");
        return "administration/user-form";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute("user") User user, @RequestParam("password") String password,
            @RequestParam(name = "applicationIds", required = false) List<Long> applicationIds,
            @RequestParam(name = "primaryApplicationId", required = false) Long primaryApplicationId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "false") boolean platformAdmin) {
        userService.create(user, password, parseRoleAssignments(applicationIds, allParams), primaryApplicationId, platformAdmin);
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
        model.addAttribute("primaryApplicationId", userService.findPrimaryApplicationId(id));
        model.addAttribute("selectedApplicationIds", roleAssignments.keySet());
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("currentPage", "users");
        return "administration/user-form";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Long id, @ModelAttribute("user") User user,
            @RequestParam("password") String password,
            @RequestParam(name = "applicationIds", required = false) List<Long> applicationIds,
            @RequestParam(name = "primaryApplicationId", required = false) Long primaryApplicationId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "false") boolean platformAdmin) {
        userService.update(id, user.getFullName(), user.getEmail(), user.isEnabled(), password,
                parseRoleAssignments(applicationIds, allParams), primaryApplicationId, platformAdmin);
        return "redirect:/administration/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggleUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user.getRoles().stream().noneMatch(role -> "ADMIN".equals(role.getName())))
            userService.toggleEnabled(id);
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
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid role selected for application " + applicationId + ".");
            }
        }
        return assignments;
    }
}
