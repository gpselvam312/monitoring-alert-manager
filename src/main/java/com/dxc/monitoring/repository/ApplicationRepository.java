package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.Application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>
{
    Optional<Application> findByName(String name);

    java.util.List<Application> findByEnabledTrueOrderByNameAsc();

    @Query("""
            SELECT a
            FROM Application a
            WHERE a.id IN :applicationIds
              AND (
                    LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<Application> findAllForApplications(@Param("search") String search,
            @Param("applicationIds") java.util.List<Long> applicationIds, Pageable pageable);

    @Query("""
            SELECT a
            FROM Application a
            WHERE
                LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Application> findAllForList(@Param("search") String search, Pageable pageable);
}