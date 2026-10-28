package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.AlertEvaluationRequest;

public interface AlertEvaluationRequestRepository extends JpaRepository<AlertEvaluationRequest, Long> {
    @Query(value = """
            SELECT *
            FROM ra_fcb.alert_evaluation_requests
            WHERE (
                status IN ('PENDING', 'FAILED')
                AND next_attempt_at <= CURRENT_TIMESTAMP
                AND attempt_count < :maxAttempts
            ) OR (
                status = 'PROCESSING'
                AND locked_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                AND attempt_count < :maxAttempts
            )
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AlertEvaluationRequest> lockDueRequests(
            @Param("batchSize") int batchSize, @Param("maxAttempts") int maxAttempts);
}
