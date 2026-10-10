package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = { "applications", "roles", "roles.permissions" })
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = { "applications", "roles" })
    Optional<User> findWithApplicationsById(Long id);
}