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
            LEFT JOIN FETCH m.environment
            WHERE m.id = :id
            """)
    Optional<Machine> findByIdWithEnvironment(Long id);
}