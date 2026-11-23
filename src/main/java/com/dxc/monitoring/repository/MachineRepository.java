package com.dxc.monitoring.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.Machine;

public interface MachineRepository extends JpaRepository<Machine, Long>
{

    @EntityGraph(attributePaths = { "environment" })
    @Query("""
            SELECT m
            FROM Machine m
            LEFT JOIN m.environment e
            WHERE
                LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.hostname, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.ipAddress, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Machine> findAllForList(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT m
            FROM Machine m
            LEFT JOIN FETCH m.environment e
            LEFT JOIN FETCH e.application
            WHERE m.id = :id
            """)
    Optional<Machine> findByIdWithEnvironment(Long id);

    @EntityGraph(attributePaths = { "environment", "environment.application" })
    @Query("""
            SELECT m
            FROM Machine m
            JOIN m.environment e
            WHERE e.application.id IN :applicationIds
              AND e.enabled = true AND e.application.enabled = true
              AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.hostname, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.ipAddress, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Machine> findAllForApplications(@Param("search") String search,
            @Param("applicationIds") java.util.List<Long> applicationIds, Pageable pageable);

    @EntityGraph(attributePaths = { "environment", "environment.application" })
    @Query("""
            SELECT m
            FROM Machine m
            JOIN m.environment e
            WHERE e.application.id IN :applicationIds
              AND e.enabled = true AND e.application.enabled = true AND m.enabled = true
            ORDER BY m.name
            """)
    java.util.List<Machine> findAllForApplications(
            @Param("applicationIds") java.util.List<Long> applicationIds);

    boolean existsByEnvironment_Id(Long environmentId);
}