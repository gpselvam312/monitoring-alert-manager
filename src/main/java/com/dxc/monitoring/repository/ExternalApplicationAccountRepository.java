package com.dxc.monitoring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.ExternalApplicationAccount;

public interface ExternalApplicationAccountRepository extends JpaRepository<ExternalApplicationAccount, Long>
{
    Optional<ExternalApplicationAccount> findByExternalApplication_Id(Long externalApplicationId);
}