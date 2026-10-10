package com.dxc.monitoring.service.dashboard;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class DashboardAccessService {
    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final UserRepository users;

    public DashboardAccessService(ApplicationRepository applications, EnvironmentRepository environments,
            UserRepository users) {
        this.applications = applications;
        this.environments = environments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Application> getAccessibleApplications() {
        if (isAdmin()) return applications.findByEnabledTrueOrderByNameAsc();
        return currentUser().getApplications().stream().filter(Application::isEnabled)
                .sorted(Comparator.comparing(Application::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessApplication(Long applicationId) {
        if (applicationId == null) throw new AccessDeniedException("An application must be selected.");
        Application app = applications.findById(applicationId).filter(Application::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Application is unavailable."));
        if (!isAdmin() && getAccessibleApplications().stream().noneMatch(a -> a.getId().equals(app.getId())))
            throw new AccessDeniedException("You are not authorized to access this application.");
    }

    /**
     * Environments are a shared global master. Application authorization is checked
     * separately; an environment does not grant or restrict access to an application.
     */
    @Transactional(readOnly = true)
    public List<Environment> getEnvironmentsForApplication(Long applicationId) {
        assertCanAccessApplication(applicationId);
        return environments.findByEnabledTrueOrderByName().stream()
                .sorted(Comparator.comparing(Environment::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId) {
        assertCanAccessApplication(applicationId);
        if (environmentId == null || environments.findById(environmentId)
                .filter(Environment::isEnabled).isEmpty())
            throw new AccessDeniedException("The selected environment is unavailable.");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessTab(com.dxc.monitoring.entity.DashboardTab tab) {
        if (tab == null || tab.getApplication() == null || tab.getEnvironment() == null)
            throw new AccessDeniedException("This dashboard tab is not assigned to an application and environment.");
        assertCanAccessEnvironment(tab.getApplication().getId(), tab.getEnvironment().getId());
    }

    private boolean isAdmin() {
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
