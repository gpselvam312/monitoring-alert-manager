package com.dxc.monitoring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<User> findPage(String search, Pageable pageable) {
        String query = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        return userRepository.search(query, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findRoleAssignmentSummaries(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Map<Long, List<String>> grouped = new HashMap<>();
        for (Object[] row : userApplicationRoleRepository.findRoleAssignmentSummaries(userIds)) {
            Long userId = (Long) row[0];
            grouped.computeIfAbsent(userId, ignored -> new java.util.ArrayList<>()).add(String.valueOf(row[1]));
        }
        Map<Long, String> result = new HashMap<>();
        grouped.forEach((id, names) -> {
            names.sort(String.CASE_INSENSITIVE_ORDER);
            result.put(id, String.join(", ", names));
        });
        return result;
    }

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

    @Transactional(readOnly = true)
    public Long findPrimaryApplicationId(Long userId) {
        return userApplicationRoleRepository.findFirstByUser_IdAndPrimaryTrue(userId)
                .map(assignment -> assignment.getApplication().getId())
                .orElse(null);
    }

    @Transactional
    public User create(User user, String password, Map<Long, Long> roleAssignments,
            Long primaryApplicationId, boolean platformAdmin) {
        user.setPasswordHash(passwordEncoder.encode(password));
        setPlatformAdmin(user, platformAdmin);
        User saved = userRepository.save(user);
        replaceRoleAssignments(saved, roleAssignments, primaryApplicationId);
        return saved;
    }

    @Transactional
    public User update(Long id, String fullName, String email, boolean enabled, String password,
            Map<Long, Long> roleAssignments, Long primaryApplicationId, boolean platformAdmin) {
        User existing = findById(id);
        existing.setFullName(fullName);
        existing.setEmail(email);
        boolean currentlyPlatformAdmin = existing.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()));
        existing.setEnabled(currentlyPlatformAdmin || platformAdmin || enabled);
        setPlatformAdmin(existing, platformAdmin);
        if (password != null && !password.isBlank()) existing.setPasswordHash(passwordEncoder.encode(password));
        User saved = userRepository.save(existing);
        replaceRoleAssignments(saved, roleAssignments, primaryApplicationId);
        return saved;
    }

    private void setPlatformAdmin(User user, boolean platformAdmin) {
        Role adminRole = roleRepository.findByNameForUpdate("ADMIN")
                .orElseThrow(() -> new IllegalArgumentException("ADMIN role is not configured."));
        boolean currentlyAdmin = user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()));
        if (platformAdmin) {
            user.getRoles().add(adminRole);
        } else if (currentlyAdmin) {
            long adminCount = userRepository.countByRoles_Name("ADMIN");
            if (adminCount <= 1)
                throw new IllegalArgumentException("At least one platform administrator must remain enabled.");
            user.getRoles().removeIf(role -> "ADMIN".equals(role.getName()));
        }
    }

    private void replaceRoleAssignments(User user, Map<Long, Long> roleAssignments, Long requestedPrimaryApplicationId) {
        if (roleAssignments == null || roleAssignments.isEmpty()) {
            userApplicationRoleRepository.deleteAllAssignmentsForUser(user.getId());
            return;
        }
        if (roleAssignments.containsKey(null) || roleAssignments.containsValue(null)) {
            throw new IllegalArgumentException("Every application assignment must include a valid application and role.");
        }

        List<Application> applications = applicationRepository.findAllById(roleAssignments.keySet());
        if (applications.size() != roleAssignments.size())
            throw new IllegalArgumentException("One or more selected applications do not exist.");
        if (applications.stream().anyMatch(application -> !application.isEnabled()))
            throw new IllegalArgumentException("Disabled applications cannot be assigned to users.");

        List<Role> assignedRoles = roleRepository.findAllById(roleAssignments.values());
        if (assignedRoles.size() != roleAssignments.values().stream().distinct().count())
            throw new IllegalArgumentException("One or more selected roles do not exist.");

        Long primaryApplicationId = requestedPrimaryApplicationId;
        if (primaryApplicationId == null) {
            primaryApplicationId = roleAssignments.keySet().stream().min(Long::compareTo).orElseThrow();
        }
        if (!roleAssignments.containsKey(primaryApplicationId)) {
            throw new IllegalArgumentException("The primary application must be one of the user's assigned applications.");
        }

        Map<Long, Application> apps = applications.stream()
                .collect(Collectors.toMap(Application::getId, a -> a));
        Map<Long, Role> roles = assignedRoles.stream()
                .collect(Collectors.toMap(Role::getId, r -> r));

        userApplicationRoleRepository.deleteAllAssignmentsForUser(user.getId());
        for (Map.Entry<Long, Long> entry : roleAssignments.entrySet()) {
            UserApplicationRole assignment = new UserApplicationRole();
            assignment.setId(new UserApplicationRoleId(user.getId(), entry.getKey()));
            assignment.setUser(user);
            assignment.setApplication(apps.get(entry.getKey()));
            assignment.setRole(roles.get(entry.getValue()));
            assignment.setPrimary(entry.getKey().equals(primaryApplicationId));
            userApplicationRoleRepository.save(assignment);
        }
    }

    @Transactional
    public void toggleEnabled(Long id) {
        User user = findById(id);
        if (user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()))) {
            user.setEnabled(true);
            userRepository.save(user);
            return;
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }
}
