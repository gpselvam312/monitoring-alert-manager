package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.name = :name")
    Optional<Role> findByNameForUpdate(@Param("name") String name);

    @Query(value = """
            select r from Role r
            where :search = ''
               or lower(r.name) like concat('%', :search, '%')
               or lower(coalesce(r.description, '')) like concat('%', :search, '%')
               or exists (
                    select permission.id
                    from Role permissionRole
                    join permissionRole.permissions permission
                    where permissionRole = r
                      and lower(permission.name) like concat('%', :search, '%')
               )
            """,
            countQuery = """
            select count(r.id) from Role r
            where :search = ''
               or lower(r.name) like concat('%', :search, '%')
               or lower(coalesce(r.description, '')) like concat('%', :search, '%')
               or exists (
                    select permission.id
                    from Role permissionRole
                    join permissionRole.permissions permission
                    where permissionRole = r
                      and lower(permission.name) like concat('%', :search, '%')
               )
            """)
    Page<Role> search(@Param("search") String search, Pageable pageable);
}
