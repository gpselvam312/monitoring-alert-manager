package com.dxc.monitoring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.NotificationChannelSecret;
import com.dxc.monitoring.entity.NotificationChannelSecretId;

public interface NotificationChannelSecretRepository extends JpaRepository<NotificationChannelSecret, NotificationChannelSecretId> {}
