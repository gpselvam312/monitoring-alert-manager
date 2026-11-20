package com.dxc.monitoring.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.ExternalApplicationAccount;

public interface ExternalApplicationAccountRepository extends JpaRepository<ExternalApplicationAccount, Long>
{
    Optional<ExternalApplicationAccount> findByExternalApplication_Id(Long externalApplicationId);

    @Query("""
            select account.externalApplication.id from ExternalApplicationAccount account
            where account.externalApplication.id in :externalApplicationIds
              and (account.username is not null or account.encryptedSecret is not null
                   or account.secretReference is not null)
            """)
    List<Long> findConfiguredExternalApplicationIds(@Param("externalApplicationIds") Collection<Long> externalApplicationIds);
}