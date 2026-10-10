package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.repository.RoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/administration/roles")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RoleManagementController {
    private final RoleRepository roleRepository;

    public RoleManagementController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public String listRoles(Model model) {
        List<Role> roles = roleRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        model.addAttribute("roles", roles);
        model.addAttribute("currentPage", "roles");
        return "administration/roles";
    }
}
