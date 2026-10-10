package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.UserApplicationRole;
import com.dxc.monitoring.entity.UserApplicationRoleId;

public interface UserApplicationRoleRepository extends JpaRepository<UserApplicationRole, UserApplicationRoleId> {
    @Query("select distinct uar from UserApplicationRole uar join fetch uar.application join fetch uar.role r left join fetch r.permissions where uar.user.id = :userId")
    List<UserApplicationRole> findAssignmentsForUser(@Param("userId") Long userId);

    Optional<UserApplicationRole> findByUser_IdAndApplication_Id(Long userId, Long applicationId);

    void deleteByUser_IdAndApplication_Id(Long userId, Long applicationId);
}
