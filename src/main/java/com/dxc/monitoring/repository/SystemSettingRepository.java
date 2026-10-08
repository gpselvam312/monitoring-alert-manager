package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long>
{
    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findAllByOrderBySettingKeyAsc();
}
