package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.ExternalApplication;

public interface ExternalApplicationRepository extends JpaRepository<ExternalApplication, Long>
{
    List<ExternalApplication> findAllByOrderByApplicationNameAsc();
    Optional<ExternalApplication> findByApplicationCodeIgnoreCase(String applicationCode);
    boolean existsByApplicationCodeIgnoreCaseAndIdNot(String applicationCode, Long id);
}