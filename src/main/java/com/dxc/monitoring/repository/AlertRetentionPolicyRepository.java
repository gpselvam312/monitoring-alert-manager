package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.AlertRetentionPolicy;

public interface AlertRetentionPolicyRepository extends JpaRepository<AlertRetentionPolicy, Long> {
    @EntityGraph(attributePaths = {"application", "environment"})
    List<AlertRetentionPolicy> findAllByOrderByDataTypeAsc();

    @Query("""
            select case when count(p) > 0 then true else false end
            from AlertRetentionPolicy p
            where p.dataType = :dataType
              and ((:applicationId is null and p.application is null) or p.application.id = :applicationId)
              and ((:environmentId is null and p.environment is null) or p.environment.id = :environmentId)
              and p.alertType = :alertType
            """)
    boolean existsForScope(@Param("dataType") AlertRetentionPolicy.DataType dataType,
            @Param("applicationId") Long applicationId, @Param("environmentId") Long environmentId,
            @Param("alertType") String alertType);
}
