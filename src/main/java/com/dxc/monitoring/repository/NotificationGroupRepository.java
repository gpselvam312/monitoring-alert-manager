package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.NotificationGroup;

public interface NotificationGroupRepository extends JpaRepository<NotificationGroup, Long> {
    List<NotificationGroup> findAllByOrderByNameAsc();
    List<NotificationGroup> findByEnabledTrueOrderByNameAsc();
}
