package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.ExternalApplication;

public interface ExternalApplicationRepository extends JpaRepository<ExternalApplication, Long>
{
    List<ExternalApplication> findAllByOrderByApplicationNameAsc();

    @Query("""
            select application
            from ExternalApplication application
            where :search = ''
               or lower(application.applicationCode) like lower(concat('%', :search, '%'))
               or lower(application.applicationName) like lower(concat('%', :search, '%'))
               or lower(application.baseUrl) like lower(concat('%', :search, '%'))
               or lower(application.authType) like lower(concat('%', :search, '%'))
            """)
    Page<ExternalApplication> search(@Param("search") String search, Pageable pageable);

    Optional<ExternalApplication> findByApplicationCodeIgnoreCase(String applicationCode);
    boolean existsByApplicationCodeIgnoreCaseAndIdNot(String applicationCode, Long id);
}
