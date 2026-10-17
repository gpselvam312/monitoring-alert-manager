package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.ExternalApplication;

public interface ExternalApplicationRepository extends JpaRepository<ExternalApplication, Long>
{
    @EntityGraph(attributePaths = {"application", "environment"})
    List<ExternalApplication> findAllByOrderByApplicationNameAsc();

    @EntityGraph(attributePaths = {"application", "environment"})
    @Query("""
            select externalApplication
            from ExternalApplication externalApplication
            left join externalApplication.application application
            left join externalApplication.environment environment
            where (:applicationId is null or application.id = :applicationId)
              and (:environmentId is null or environment.id = :environmentId)
              and (:externalType is null or externalApplication.externalType = :externalType)
              and (
                    :search = ''
                    or lower(externalApplication.applicationCode) like lower(concat('%', :search, '%'))
                    or lower(externalApplication.applicationName) like lower(concat('%', :search, '%'))
                    or lower(externalApplication.baseUrl) like lower(concat('%', :search, '%'))
                    or lower(externalApplication.authType) like lower(concat('%', :search, '%'))
                    or lower(externalApplication.externalType) like lower(concat('%', :search, '%'))
                    or lower(coalesce(application.name, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(environment.name, '')) like lower(concat('%', :search, '%'))
              )
            """)
    Page<ExternalApplication> search(@Param("search") String search,
            @Param("applicationId") Long applicationId,
            @Param("environmentId") Long environmentId,
            @Param("externalType") String externalType, Pageable pageable);

    Optional<ExternalApplication> findByApplicationCodeIgnoreCase(String applicationCode);
    boolean existsByApplicationCodeIgnoreCaseAndIdNot(String applicationCode, Long id);
}