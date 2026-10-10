package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
}
