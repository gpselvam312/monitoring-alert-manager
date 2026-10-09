package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long>
{
    @Query("""
            SELECT s
            FROM Schedule s
            WHERE
                LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.cronExpression, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Schedule> findAllForList(@Param("search") String search, Pageable pageable);
}