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

    @EntityGraph(attributePaths = {"application", "environment"})
    @Query("select p from AlertRetentionPolicy p where p.id = :id")
    java.util.Optional<AlertRetentionPolicy> findByIdWithScope(@Param("id") Long id);

    @EntityGraph(attributePaths = {"application", "environment"})
    @Query("select p from AlertRetentionPolicy p")
    List<AlertRetentionPolicy> findAllWithScope();

    @Query(value = """
            select exists (
                select 1
                from ra_fcb.alert_retention_policies p
                where p.data_type = :dataType
                  and p.application_id is not distinct from :applicationId
                  and p.environment_id is not distinct from :environmentId
                  and p.alert_type = :alertType
            )
            """, nativeQuery = true)
    boolean existsForScope(@Param("dataType") String dataType,
            @Param("applicationId") Long applicationId, @Param("environmentId") Long environmentId,
            @Param("alertType") String alertType);
}
