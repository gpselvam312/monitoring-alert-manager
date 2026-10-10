package com.dxc.monitoring.service.dashboard;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class DashboardAccessService {
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final DashboardTabRepository tabs;
    private final MonitoringJobRepository jobs;

    public DashboardAccessService(ApplicationRepository applications, UserRepository users,
            DashboardTabRepository tabs, MonitoringJobRepository jobs) {
        this.applications = applications;
        this.users = users;
        this.tabs = tabs;
        this.jobs = jobs;
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

    @Transactional(readOnly = true)
    public List<Environment> getEnvironmentsForApplication(Long applicationId) {
        assertCanAccessApplication(applicationId);
        Map<Long, Environment> scoped = new LinkedHashMap<>();
        tabs.findAllByApplication_IdAndEnabledTrueOrderBySortOrderAsc(applicationId).stream()
                .filter(tab -> tab.getApplication() != null && tab.getApplication().getId().equals(applicationId))
                .map(DashboardTab::getEnvironment).filter(e -> e != null && e.isEnabled())
                .forEach(e -> scoped.putIfAbsent(e.getId(), e));
        jobs.findDistinctEnabledEnvironmentsByApplicationId(applicationId)
                .forEach(e -> scoped.putIfAbsent(e.getId(), e));
        return scoped.values().stream().sorted(Comparator.comparing(Environment::getName,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId) {
        assertCanAccessApplication(applicationId);
        if (environmentId == null || getEnvironmentsForApplication(applicationId).stream()
                .noneMatch(e -> e.getId().equals(environmentId)))
            throw new AccessDeniedException("You are not authorized to access this environment.");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessTab(DashboardTab tab) {
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
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication is required.");
        return users.findByUsername(auth.getName()).orElseThrow(() -> new AccessDeniedException("Authenticated user was not found."));
    }
}
