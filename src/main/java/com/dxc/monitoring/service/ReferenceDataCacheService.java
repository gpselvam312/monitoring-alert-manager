package com.dxc.monitoring.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;

@Service
public class ReferenceDataCacheService {
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;

    public ReferenceDataCacheService(ApplicationRepository applicationRepository,
            EnvironmentRepository environmentRepository) {
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "enabledApplications")
    public List<Application> getEnabledApplications() {
        return List.copyOf(applicationRepository.findByEnabledTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "enabledEnvironmentsByApplication", key = "#applicationId")
    public List<Environment> getEnabledEnvironments(Long applicationId) {
        List<Environment> environments = environmentRepository
                .findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(applicationId);
        // Ensure fields used by dropdown templates are initialized before the cached result
        // outlives this read-only transaction.
        environments.forEach(environment -> {
            if (environment.getApplication() != null) {
                environment.getApplication().getName();
            }
        });
        return List.copyOf(environments);
    }
}