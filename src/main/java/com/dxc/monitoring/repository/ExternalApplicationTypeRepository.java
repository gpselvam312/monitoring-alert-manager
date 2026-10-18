package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.ExternalApplicationType;

public interface ExternalApplicationTypeRepository extends JpaRepository<ExternalApplicationType, String>
{
    List<ExternalApplicationType> findByEnabledTrueOrderBySortOrderAscDisplayNameAsc();
    Optional<ExternalApplicationType> findByCodeIgnoreCaseAndEnabledTrue(String code);
}