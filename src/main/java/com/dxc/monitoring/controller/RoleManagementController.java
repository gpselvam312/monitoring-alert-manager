package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Permission;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.repository.PermissionRepository;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserApplicationRoleRepository userApplicationRoleRepository;

    public RoleManagementController(RoleRepository roleRepository, PermissionRepository permissionRepository, UserRepository userRepository,
            UserApplicationRoleRepository userApplicationRoleRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.userApplicationRoleRepository = userApplicationRoleRepository;
    }

    @GetMapping
    public String listRoles(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateRoleTable(model, page, size, search, sort, direction);
        return "administration/roles";
    }

    @GetMapping("/table")
    public String roleTable(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateRoleTable(model, page, size, search, sort, direction);
        return "administration/roles :: rolesTable";
    }

    private void populateRoleTable(Model model, int page, int size, String search, String sort, String direction) {
        int safeSize = (size == 5 || size == 10 || size == 25) ? size : 5;
        String query = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Comparator<Role> comparator = "description".equals(sort)
                ? java.util.Comparator.comparing(r -> r.getDescription() == null ? "" : r.getDescription(), String.CASE_INSENSITIVE_ORDER)
                : java.util.Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER);
        if ("desc".equalsIgnoreCase(direction)) comparator = comparator.reversed();
        java.util.List<Role> filtered = roleRepository.findAll().stream()
                .filter(role -> query.isBlank()
                        || role.getName().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || (role.getDescription() != null && role.getDescription().toLowerCase(java.util.Locale.ROOT).contains(query))
                        || role.getPermissions().stream().anyMatch(permission -> permission.getName().toLowerCase(java.util.Locale.ROOT).contains(query)))
                .sorted(comparator).toList();
        int totalPages = (int) Math.ceil((double) filtered.size() / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Role> rolePage = new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
        model.addAttribute("rolePage", rolePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("search", search == null ? "" : search.trim());
        model.addAttribute("currentPage", "roles");
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("role", new Role());
        model.addAttribute("permissions", permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("selectedPermissionIds", Set.of());
        model.addAttribute("pageTitle", "Add Role");
        model.addAttribute("currentPage", "roles");
        return "administration/role-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Role role = findRole(id);
        model.addAttribute("role", role);
        model.addAttribute("permissions", permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("selectedPermissionIds", role.getPermissions().stream().map(Permission::getId).collect(java.util.stream.Collectors.toSet()));
        model.addAttribute("pageTitle", "Edit Role");
        model.addAttribute("currentPage", "roles");
        return "administration/role-form";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@ModelAttribute("role") Role formRole,
            @RequestParam(name = "permissionIds", required = false) java.util.List<Long> permissionIds,
            RedirectAttributes flash) {
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
        if (!"ADMIN".equals(role.getName())) {
            java.util.List<Long> ids = permissionIds == null ? java.util.List.of() : permissionIds.stream().distinct().toList();
            java.util.List<Permission> selected = permissionRepository.findAllById(ids);
            if (selected.size() != ids.size()) {
                flash.addFlashAttribute("errorMessage", "One or more selected permissions no longer exist.");
                return formUrl;
            }
            role.setPermissions(new java.util.HashSet<>(selected));
        }
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
