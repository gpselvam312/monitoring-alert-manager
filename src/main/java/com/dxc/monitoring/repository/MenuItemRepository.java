package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long>
{
    List<MenuItem> findByEnabledTrueOrderBySectionOrderAscDisplayOrderAsc();
}
