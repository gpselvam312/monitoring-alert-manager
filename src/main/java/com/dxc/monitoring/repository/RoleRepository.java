package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Query(value = """
            select distinct r from Role r left join r.permissions p
            where :search = ''
               or lower(r.name) like concat('%', :search, '%')
               or lower(coalesce(r.description, '')) like concat('%', :search, '%')
               or lower(p.name) like concat('%', :search, '%')
            """,
            countQuery = """
            select count(distinct r.id) from Role r left join r.permissions p
            where :search = ''
               or lower(r.name) like concat('%', :search, '%')
               or lower(coalesce(r.description, '')) like concat('%', :search, '%')
               or lower(p.name) like concat('%', :search, '%')
            """)
    Page<Role> search(@Param("search") String search, Pageable pageable);
}
