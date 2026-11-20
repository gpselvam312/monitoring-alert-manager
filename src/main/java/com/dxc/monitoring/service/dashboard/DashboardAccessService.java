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
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.ReferenceDataCacheService;

@Service
public class DashboardAccessService {
    private final ReferenceDataCacheService referenceDataCache;

    public DashboardAccessService(ReferenceDataCacheService referenceDataCache) {
        this.referenceDataCache = referenceDataCache;
    }

    @Transactional(readOnly = true)
    public List<Application> getAccessibleApplications() {
        return getApplicationsWithPermission("MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public Long getPrimaryApplicationId() {
        return currentUserDetails().getPrimaryApplicationId();
    }

    @Transactional(readOnly = true)
    public List<Application> getApplicationsWithPermission(String permission) {
        List<Application> enabled = referenceDataCache.getEnabledApplications();
        if (isPlatformAdmin()) return enabled;
        CustomUserDetails user = currentUserDetails();
        return enabled.stream()
                .filter(application -> user.hasApplicationPermission(application.getId(), permission))
                .sorted(Comparator.comparing(Application::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessApplication(Long applicationId) {
        assertCanAccessApplication(applicationId, "MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessApplication(Long applicationId, String permission) {
        if (applicationId == null) throw new AccessDeniedException("An application must be selected.");
        Application application = referenceDataCache.getEnabledApplications().stream()
                .filter(candidate -> applicationId.equals(candidate.getId()))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Application is unavailable."));
        if (isPlatformAdmin()) return;
        if (!currentUserDetails().hasApplicationPermission(application.getId(), permission))
            throw new AccessDeniedException("Your role does not allow this action for the selected application.");
    }

    @Transactional(readOnly = true)
    public List<Environment> getEnvironmentsForApplication(Long applicationId) {
        assertCanAccessApplication(applicationId, "MONITORING_VIEW");
        return referenceDataCache.getEnabledEnvironments(applicationId);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId) {
        assertCanAccessEnvironment(applicationId, environmentId, "MONITORING_VIEW");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEnvironment(Long applicationId, Long environmentId, String permission) {
        assertCanAccessApplication(applicationId, permission);
        boolean belongsToApplication = environmentId != null
                && referenceDataCache.getEnabledEnvironments(applicationId).stream()
                        .anyMatch(environment -> environmentId.equals(environment.getId())
                                && environment.getApplication() != null
                                && applicationId.equals(environment.getApplication().getId()));
        if (!belongsToApplication)
            throw new AccessDeniedException("The selected environment does not belong to this application or is disabled.");
    }

    @Transactional(readOnly = true)
    public void assertCanAccessTab(com.dxc.monitoring.entity.DashboardTab tab) {
        if (tab == null || tab.getApplication() == null || tab.getEnvironment() == null)
            throw new AccessDeniedException("This dashboard tab is not assigned to an application and environment.");
        assertCanAccessEnvironment(tab.getApplication().getId(), tab.getEnvironment().getId(), "MONITORING_VIEW");
    }

    private boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private CustomUserDetails currentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails))
            throw new AccessDeniedException("Authenticated user details are unavailable.");
        return userDetails;
    }
}
