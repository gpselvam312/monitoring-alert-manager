package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertType;

public interface AlertTypeRepository extends JpaRepository<AlertType, String> {
    List<AlertType> findByEnabledTrueOrderBySortOrderAscNameAsc();
}
