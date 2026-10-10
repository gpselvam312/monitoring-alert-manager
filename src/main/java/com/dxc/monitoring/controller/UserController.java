package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/administration/users")
@PreAuthorize("hasAuthority('USER_CONFIG')")
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
        model.addAttribute("users", userService.findAll());
        model.addAttribute("currentPage", "users");

        return "administration/users";
    }

    @GetMapping("/new")
    public String createUserForm(Model model)
    {
        User user = new User();
        user.setEnabled(true);

        model.addAttribute("user", user);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("selectedApplicationIds", java.util.List.of());
        model.addAttribute("pageTitle", "Add User");
        model.addAttribute("currentPage", "users");

        return "administration/user-form";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute("user") User user, @RequestParam("password") String password,
            @RequestParam("roleId") Long roleId, @RequestParam(name = "applicationIds", required = false) java.util.List<Long> applicationIds)
    {
        userService.create(user, password, roleId, applicationIds);

        return "redirect:/administration/users";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model)
    {
        User user = userService.findById(id);

        model.addAttribute("user", user);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("applications", applicationRepository.findByEnabledTrueOrderByNameAsc());
        model.addAttribute("selectedApplicationIds", user.getApplications().stream().map(Application::getId).toList());
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("currentPage", "users");

        return "administration/user-form";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Long id, @ModelAttribute("user") User user,
            @RequestParam("password") String password, @RequestParam("roleId") Long roleId,
            @RequestParam(name = "applicationIds", required = false) java.util.List<Long> applicationIds)
    {
        userService.update(id, user.getFullName(), user.getEmail(), user.isEnabled(), password, roleId, applicationIds);

        return "redirect:/administration/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggleUser(@PathVariable Long id)
    {
        userService.toggleEnabled(id);

        return "redirect:/administration/users";
    }

}
