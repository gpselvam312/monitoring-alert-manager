package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.NotificationRecipient;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    List<NotificationRecipient> findByEnabledTrueOrderByNameAsc();
}
