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

    java.util.List<Application> findByEnabledTrueOrderByName();

    @Query("""
            SELECT a
            FROM Application a
            WHERE
                LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Application> findAllForList(@Param("search") String search, Pageable pageable);
}