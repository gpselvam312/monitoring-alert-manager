package com.dxc.monitoring.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.entity.UserApplicationRoleId;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationRepository applicationRepository;
    private final UserApplicationRoleRepository userApplicationRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            ApplicationRepository applicationRepository, UserApplicationRoleRepository userApplicationRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationRepository = applicationRepository;
        this.userApplicationRoleRepository = userApplicationRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() { return userRepository.findAll(); }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Role> findAllRoles() { return roleRepository.findAll(); }

    @Transactional(readOnly = true)
    public String findRoleAssignmentSummary(Long userId) {
        return userApplicationRoleRepository.findAssignmentsForUser(userId).stream()
                .map(a -> a.getApplication().getName() + " — " + a.getRole().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> findRoleAssignments(Long userId) {
        return userApplicationRoleRepository.findAssignmentsForUser(userId).stream()
                .collect(Collectors.toMap(a -> a.getApplication().getId(), a -> a.getRole().getId()));
    }

    @Transactional
    public User create(User user, String password, Map<Long, Long> roleAssignments, boolean platformAdmin) {
        user.setPasswordHash(passwordEncoder.encode(password));
        setPlatformAdmin(user, platformAdmin);
        Set<Application> assignedApplications = resolveApplications(roleAssignments);
        user.setApplications(assignedApplications);
        User saved = userRepository.save(user);
        replaceRoleAssignments(saved, roleAssignments);
        return saved;
    }

    @Transactional
    public User update(Long id, String fullName, String email, boolean enabled, String password,
            Map<Long, Long> roleAssignments, boolean platformAdmin) {
        User existing = findById(id);
        existing.setFullName(fullName);
        existing.setEmail(email);
        existing.setEnabled(enabled);
        setPlatformAdmin(existing, platformAdmin);
        if (password != null && !password.isBlank()) existing.setPasswordHash(passwordEncoder.encode(password));
        existing.setApplications(resolveApplications(roleAssignments));
        User saved = userRepository.save(existing);
        replaceRoleAssignments(saved, roleAssignments);
        return saved;
    }

    private void setPlatformAdmin(User user, boolean platformAdmin) {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalArgumentException("ADMIN role is not configured."));
        boolean currentlyAdmin = user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()));
        if (platformAdmin) {
            user.getRoles().add(adminRole);
        } else if (currentlyAdmin) {
            long adminCount = userRepository.findAll().stream()
                    .filter(existing -> existing.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName())))
                    .count();
            if (adminCount <= 1)
                throw new IllegalArgumentException("At least one platform administrator must remain enabled.");
            user.getRoles().removeIf(role -> "ADMIN".equals(role.getName()));
        }
    }

    private Set<Application> resolveApplications(Map<Long, Long> roleAssignments) {
        if (roleAssignments == null || roleAssignments.isEmpty()) return new HashSet<>();
        List<Application> found = applicationRepository.findAllById(roleAssignments.keySet());
        if (found.size() != roleAssignments.keySet().size())
            throw new IllegalArgumentException("One or more selected applications do not exist.");
        if (found.stream().anyMatch(app -> !app.isEnabled()))
            throw new IllegalArgumentException("Disabled applications cannot be assigned to users.");
        if (roleAssignments.values().stream().anyMatch(id -> id == null))
            throw new IllegalArgumentException("Select a role for every assigned application.");
        List<Role> roles = roleRepository.findAllById(roleAssignments.values());
        if (roles.size() != roleAssignments.values().stream().distinct().count())
            throw new IllegalArgumentException("One or more selected roles do not exist.");
        return new HashSet<>(found);
    }

    private void replaceRoleAssignments(User user, Map<Long, Long> roleAssignments) {
        userApplicationRoleRepository.deleteAllAssignmentsForUser(user.getId());
        if (roleAssignments == null || roleAssignments.isEmpty()) return;

        Map<Long, Application> apps = applicationRepository.findAllById(roleAssignments.keySet()).stream()
                .collect(Collectors.toMap(Application::getId, a -> a));
        Map<Long, Role> roles = roleRepository.findAllById(roleAssignments.values()).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        for (Map.Entry<Long, Long> entry : roleAssignments.entrySet()) {
            UserApplicationRole assignment = new UserApplicationRole();
            assignment.setId(new UserApplicationRoleId(user.getId(), entry.getKey()));
            assignment.setUser(user);
            assignment.setApplication(apps.get(entry.getKey()));
            assignment.setRole(roles.get(entry.getValue()));
            userApplicationRoleRepository.save(assignment);
        }
    }

    @Transactional
    public void toggleEnabled(Long id) {
        User user = findById(id);
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }
}
