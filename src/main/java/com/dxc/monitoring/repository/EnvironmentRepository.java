package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.Environment;

public interface EnvironmentRepository extends JpaRepository<Environment, Long>
{
    Optional<Environment> findByName(String name);

    List<Environment> findByEnabledTrueOrderByName();

    @Query("""
            select e
            from Environment e
            where lower(e.name) like lower(concat('%', :search, '%'))
               or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
            """)
    Page<Environment> findAllForList(@Param("search") String search, Pageable pageable);
}