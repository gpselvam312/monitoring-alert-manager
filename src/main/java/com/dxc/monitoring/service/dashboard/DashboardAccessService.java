package com.dxc.monitoring.service.dashboard;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.UserApplicationRoleRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class DashboardAccessService {
    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final UserRepository users;
    private final UserApplicationRoleRepository assignments;

    public DashboardAccessService(ApplicationRepository applications, EnvironmentRepository environments,
            UserRepository users, UserApplicationRoleRepository assignments) {
        this.applications = applications;
        this.environments = environments;
        this.users = users;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<Application> getAccessibleApplications() {
        return getApplicationsWithPermission("MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public List<Application> getApplicationsWithPermission(String permission) {
        if (isPlatformAdmin()) return applications.findByEnabledTrueOrderByNameAsc();
        return assignments.findAssignmentsForUser(currentUser().getId()).stream()
                .filter(a -> a.getApplication().isEnabled())
                .filter(a -> hasPermission(a.getRole(), permission))
                .map(UserApplicationRole::getApplication).distinct()
                .sorted(Comparator.comparing(Application::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessApplication(Long applicationId) {
        assertCanAccessApplication(applicationId, "MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessApplication(Long applicationId, String permission) {
        if (applicationId == null) throw new AccessDeniedException("An application must be selected.");
        Application app = applications.findById(applicationId).filter(Application::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Application is unavailable."));
        if (isPlatformAdmin()) return;
        User user = currentUser();
        UserApplicationRole assignment = assignments.findByUser_IdAndApplication_Id(user.getId(), app.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not assigned to this application."));
        if (!hasPermission(assignment.getRole(), permission))
            throw new AccessDeniedException("Your role does not allow this action for the selected application.");
    }

    @Transactional(readOnly = true)
    public List<Environment> getEnvironmentsForApplication(Long applicationId) {
        assertCanAccessApplication(applicationId, "MONITORING_VIEW");
        return environments.findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(applicationId);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId) {
        assertCanAccessEnvironment(applicationId, environmentId, "MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId, String permission) {
        assertCanAccessApplication(applicationId, permission);
        if (environmentId == null || environments.findById(environmentId)
                .filter(Environment::isEnabled)
                .filter(e -> e.getApplication() != null && e.getApplication().getId().equals(applicationId)).isEmpty())
            throw new AccessDeniedException("The selected environment does not belong to this application or is disabled.");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessTab(com.dxc.monitoring.entity.DashboardTab tab) {
        if (tab == null || tab.getApplication() == null || tab.getEnvironment() == null)
            throw new AccessDeniedException("This dashboard tab is not assigned to an application and environment.");
        assertCanAccessEnvironment(tab.getApplication().getId(), tab.getEnvironment().getId(), "MONITORING_VIEW");
    }

    private boolean hasPermission(Role role, String permission) {
        return role != null && ("ADMIN".equals(role.getName())
                || role.getPermissions().stream().anyMatch(p -> permission.equals(p.getName())));
    }

    private boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AccessDeniedException("Authentication is required.");
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found."));
    }
}
