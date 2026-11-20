package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByRoles_Id(Long roleId);
    long countByRoles_Name(String roleName);

    @Query(value = """
            select u from User u
            where :search = ''
               or lower(u.username) like concat('%', :search, '%')
               or lower(coalesce(u.fullName, '')) like concat('%', :search, '%')
               or lower(coalesce(u.email, '')) like concat('%', :search, '%')
               or exists (select r.id from User ur join ur.roles r
                          where ur.id = u.id and lower(r.name) like concat('%', :search, '%'))
            """, countQuery = """
            select count(u) from User u
            where :search = ''
               or lower(u.username) like concat('%', :search, '%')
               or lower(coalesce(u.fullName, '')) like concat('%', :search, '%')
               or lower(coalesce(u.email, '')) like concat('%', :search, '%')
               or exists (select r.id from User ur join ur.roles r
                          where ur.id = u.id and lower(r.name) like concat('%', :search, '%'))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);
}