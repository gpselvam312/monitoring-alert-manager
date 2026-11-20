package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO ra_fcb.notification_deliveries
                (alert_id, alert_history_id, channel_id, recipient_id, destination_snapshot,
                 cc_recipients_snapshot, idempotency_key, status, attempt_count, next_attempt_at,
                 last_error, message_payload, created_at, updated_at)
            VALUES
                (:alertId, :historyId, :channelId, :recipientId, :destination,
                 :ccRecipients, :idempotencyKey, :status, 0, :nextAttemptAt,
                 :lastError, CAST(:messagePayload AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("alertId") Long alertId,
            @Param("historyId") Long historyId,
            @Param("channelId") Long channelId,
            @Param("recipientId") Long recipientId,
            @Param("destination") String destination,
            @Param("ccRecipients") String ccRecipients,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("status") String status,
            @Param("nextAttemptAt") java.time.OffsetDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("messagePayload") String messagePayload);

    @EntityGraph(attributePaths = {"alert", "alertHistory", "channel", "recipient"})
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"alert", "alertHistory", "channel", "recipient"})
    Page<NotificationDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<NotificationDelivery> findTopByAlert_IdAndChannel_IdAndRecipient_IdOrderByCreatedAtDesc(
            Long alertId, Long channelId, Long recipientId);
    Optional<NotificationDelivery> findTopByAlert_IdAndChannel_IdAndRecipientIsNullOrderByCreatedAtDesc(
            Long alertId, Long channelId);

    interface ChannelDeliverySummary {
        Long getAlertId();
        String getChannelType();
        String getDeliveryStatus();
        Long getSentCount();
        Long getFailedCount();
        Long getPendingCount();
        Long getSkippedCount();
    }

    @Query(value = """
            WITH ranked_deliveries AS (
                SELECT d.alert_id, c.channel_type, d.status, d.attempt_count,
                       ROW_NUMBER() OVER (
                           PARTITION BY d.alert_id, d.channel_id, COALESCE(d.recipient_id, 0)
                           ORDER BY d.updated_at DESC, d.id DESC
                       ) AS row_number
                FROM ra_fcb.notification_deliveries d
                JOIN ra_fcb.notification_channels c ON c.id = d.channel_id
                WHERE d.alert_id IN (:alertIds)
            )
            SELECT alert_id AS "alertId",
                   channel_type AS "channelType",
                   CASE
                       WHEN SUM(CASE WHEN status IN ('PENDING', 'SENDING') OR (status = 'FAILED' AND attempt_count < 8) THEN 1 ELSE 0 END) > 0 THEN 'PENDING'
                       WHEN SUM(CASE WHEN status = 'FAILED' AND attempt_count >= 8 THEN 1 ELSE 0 END) > 0 THEN 'FAILED'
                       WHEN SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) > 0 THEN 'SENT'
                       ELSE 'SKIPPED'
                   END AS "deliveryStatus",
                   SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS "sentCount",
                   SUM(CASE WHEN status = 'FAILED' AND attempt_count >= 8 THEN 1 ELSE 0 END) AS "failedCount",
                   SUM(CASE WHEN status IN ('PENDING', 'SENDING') OR (status = 'FAILED' AND attempt_count < 8) THEN 1 ELSE 0 END) AS "pendingCount",
                   SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END) AS "skippedCount"
            FROM ranked_deliveries
            WHERE row_number = 1
            GROUP BY alert_id, channel_type
            """, nativeQuery = true)
    List<ChannelDeliverySummary> summarizeLatestByAlertIds(@Param("alertIds") List<Long> alertIds);

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
