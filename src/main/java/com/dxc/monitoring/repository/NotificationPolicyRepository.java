package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.NotificationPolicy;

public interface NotificationPolicyRepository extends JpaRepository<NotificationPolicy, Long> {
    List<NotificationPolicy> findAllByOrderByNameAsc();
    List<NotificationPolicy> findByEnabledTrueOrderByNameAsc();
}
