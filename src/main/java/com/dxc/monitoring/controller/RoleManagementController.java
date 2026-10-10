package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/administration/roles")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RoleManagementController {
    private static final Pattern ROLE_NAME = Pattern.compile("[A-Z][A-Z0-9_]{1,49}");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserApplicationRoleRepository userApplicationRoleRepository;

    public RoleManagementController(RoleRepository roleRepository, UserRepository userRepository,
            UserApplicationRoleRepository userApplicationRoleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userApplicationRoleRepository = userApplicationRoleRepository;
    }

    @GetMapping
    public String listRoles(Model model) {
        model.addAttribute("roles", roleRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("currentPage", "roles");
        return "administration/roles";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("role", new Role());
        model.addAttribute("pageTitle", "Add Role");
        model.addAttribute("currentPage", "roles");
        return "administration/role-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("role", findRole(id));
        model.addAttribute("pageTitle", "Edit Role");
        model.addAttribute("currentPage", "roles");
        return "administration/role-form";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@ModelAttribute("role") Role formRole, RedirectAttributes flash) {
        String name = formRole.getName() == null ? "" : formRole.getName().trim().toUpperCase(java.util.Locale.ROOT);
        String formUrl = formRole.getId() == null ? "redirect:/administration/roles/new" : "redirect:/administration/roles/" + formRole.getId() + "/edit";
        if (!ROLE_NAME.matcher(name).matches()) {
            flash.addFlashAttribute("errorMessage", "Role name must be 2–50 characters and use letters, numbers, or underscores.");
            return formUrl;
        }

        Role role = formRole.getId() == null ? new Role() : findRole(formRole.getId());
        if ("ADMIN".equals(role.getName()) && !name.equals("ADMIN")) {
            flash.addFlashAttribute("errorMessage", "The built-in ADMIN role cannot be renamed.");
            return "redirect:/administration/roles/" + role.getId() + "/edit";
        }
        var existing = roleRepository.findByName(name);
        if (existing.isPresent() && !existing.get().getId().equals(role.getId())) {
            flash.addFlashAttribute("errorMessage", "A role with that name already exists.");
            return formUrl;
        }

        role.setName(name);
        role.setDescription(formRole.getDescription() == null ? null : formRole.getDescription().trim());
        roleRepository.save(role);
        flash.addFlashAttribute("successMessage", "Role saved successfully.");
        return "redirect:/administration/roles";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        Role role = findRole(id);
        if ("ADMIN".equals(role.getName())) {
            flash.addFlashAttribute("errorMessage", "The built-in ADMIN role cannot be deleted.");
        } else if (userRepository.findAll().stream().anyMatch(user -> user.getRoles().stream().anyMatch(assignedRole -> id.equals(assignedRole.getId())))
                || userApplicationRoleRepository.findAll().stream().anyMatch(assignment -> id.equals(assignment.getRole().getId()))) {
            flash.addFlashAttribute("errorMessage", "This role is assigned to one or more users. Remove those assignments before deleting it.");
        } else {
            roleRepository.delete(role);
            flash.addFlashAttribute("successMessage", "Role deleted successfully.");
        }
        return "redirect:/administration/roles";
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }
}
