package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"alert", "alertHistory", "channel", "recipient"})
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"alert", "alertHistory", "channel", "recipient"})
    Page<NotificationDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<NotificationDelivery> findTopByAlert_IdAndChannel_IdAndRecipient_IdOrderByCreatedAtDesc(
            Long alertId, Long channelId, Long recipientId);
    Optional<NotificationDelivery> findTopByAlert_IdAndChannel_IdAndRecipientIsNullOrderByCreatedAtDesc(
            Long alertId, Long channelId);

    @Query(value = """
            SELECT *
            FROM ra_fcb.notification_deliveries
            WHERE (
                status IN ('PENDING', 'FAILED')
                AND next_attempt_at <= CURRENT_TIMESTAMP
                AND attempt_count < :maxAttempts
            ) OR (
                status = 'SENDING'
                AND locked_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                AND attempt_count < :maxAttempts
            )
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationDelivery> lockDueDeliveries(
            @Param("batchSize") int batchSize, @Param("maxAttempts") int maxAttempts);
}
