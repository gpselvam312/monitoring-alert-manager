package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.AlertRetentionPolicy;

public interface AlertRetentionPolicyRepository extends JpaRepository<AlertRetentionPolicy, Long> {
    List<AlertRetentionPolicy> findAllByOrderByDataTypeAsc();
    boolean existsByDataType(AlertRetentionPolicy.DataType dataType);
}
