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

    List<Environment> findByApplicationIdAndEnabledTrueOrderByNameIgnoreCase(Long applicationId);

    List<Environment> findByApplicationIdOrderByNameIgnoreCase(Long applicationId);

    List<Environment> findByApplicationIdInAndEnabledTrueOrderByNameIgnoreCase(List<Long> applicationIds);

    @Query("""
            select e
            from Environment e
            left join e.application a
            where e.application.id in :applicationIds
              and (lower(e.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(a.name, '')) like lower(concat('%', :search, '%')))
            """)
    Page<Environment> findAllForApplications(@Param("search") String search,
            @Param("applicationIds") List<Long> applicationIds, Pageable pageable);

    boolean existsByApplicationIdAndNameIgnoreCase(Long applicationId, String name);

    Optional<Environment> findByApplicationIdAndNameIgnoreCase(Long applicationId, String name);

    List<Environment> findByEnabledTrueOrderByName();

    @Query("""
            select e
            from Environment e
            left join e.application a
            where lower(e.name) like lower(concat('%', :search, '%'))
               or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(a.name, '')) like lower(concat('%', :search, '%'))
            """)
    Page<Environment> findAllForList(@Param("search") String search, Pageable pageable);
}