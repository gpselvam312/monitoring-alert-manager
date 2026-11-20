package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long>
{
    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findAllByOrderBySortOrderAscSettingKeyAsc();

    List<SystemSetting> findAllByOrderBySettingKeyAsc();

    @Query(value = """
            select s from SystemSetting s
            where :search = ''
               or lower(s.settingKey) like concat('%', :search, '%')
               or lower(coalesce(s.description, '')) like concat('%', :search, '%')
               or lower(s.settingValue) like concat('%', :search, '%')
               or lower(s.dataType) like concat('%', :search, '%')
            """, countQuery = """
            select count(s) from SystemSetting s
            where :search = ''
               or lower(s.settingKey) like concat('%', :search, '%')
               or lower(coalesce(s.description, '')) like concat('%', :search, '%')
               or lower(s.settingValue) like concat('%', :search, '%')
               or lower(s.dataType) like concat('%', :search, '%')
            """)
    Page<SystemSetting> search(@Param("search") String search, Pageable pageable);
}
