package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService
{
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, ApplicationRepository applicationRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll()
    {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User findById(Long id)
    {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Role> findAllRoles()
    {
        return roleRepository.findAll();
    }

    @Transactional
    public User create(User user, String password, Long roleId, List<Long> applicationIds)
    {
        user.setPasswordHash(passwordEncoder.encode(password));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        user.getRoles().clear();
        user.getRoles().add(role);
        user.setApplications(resolveApplications(applicationIds));

        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, String fullName, String email, boolean enabled, String password, Long roleId, List<Long> applicationIds)
    {
        User existingUser = findById(id);

        existingUser.setFullName(fullName);
        existingUser.setEmail(email);
        existingUser.setEnabled(enabled);

        /*
         * Only change the password when one was supplied. This prevents an edit operation from accidentally
         * replacing the existing password with an empty value.
         */
        if (password != null && !password.isBlank())
        {
            existingUser.setPasswordHash(passwordEncoder.encode(password));
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        existingUser.getRoles().clear();
        existingUser.getRoles().add(role);
        existingUser.setApplications(resolveApplications(applicationIds));

        return userRepository.save(existingUser);
    }

    private java.util.Set<Application> resolveApplications(List<Long> applicationIds)
    {
        if (applicationIds == null || applicationIds.isEmpty()) return new java.util.HashSet<>();
        List<Application> applications = applicationRepository.findAllById(applicationIds);
        if (applications.size() != applicationIds.stream().distinct().count())
            throw new IllegalArgumentException("One or more selected applications do not exist.");
        return new java.util.HashSet<>(applications);
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        User user = findById(id);

        user.setEnabled(!user.isEnabled());

        userRepository.save(user);
    }

}
