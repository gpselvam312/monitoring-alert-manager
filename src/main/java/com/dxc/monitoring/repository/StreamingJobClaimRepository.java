package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.StreamingJobClaim;

public interface StreamingJobClaimRepository extends JpaRepository<StreamingJobClaim, Long>
{
    @Query("""
            SELECT c
            FROM StreamingJobClaim c
            JOIN FETCH c.monitoringJob j
            LEFT JOIN FETCH j.machine
            WHERE j.id = :jobId
            """)
    Optional<StreamingJobClaim> findByJobId(@Param("jobId") Long jobId);

    @Query("""
            SELECT c
            FROM StreamingJobClaim c
            JOIN FETCH c.monitoringJob j
            LEFT JOIN FETCH j.machine
            WHERE j.executionMode = com.dxc.monitoring.entity.MonitoringJob.ExecutionMode.STREAMING
              AND j.enabled = true
            ORDER BY j.name
            """)
    List<StreamingJobClaim> findEnabledStreamingClaims();

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ra_fcb.streaming_job_claims (monitoring_job_id)
            VALUES (:jobId)
            ON CONFLICT (monitoring_job_id) DO NOTHING
            """, nativeQuery = true)
    int ensureRow(@Param("jobId") Long jobId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = 'STARTING',
                   owner_instance_id = :owner,
                   started_by = :startedBy,
                   started_at = CURRENT_TIMESTAMP,
                   heartbeat_at = CURRENT_TIMESTAMP,
                   deadline_at = CURRENT_TIMESTAMP + (:maxRuntimeSeconds * INTERVAL '1 second'),
                   finished_at = NULL,
                   exit_code = NULL,
                   error_message = NULL,
                   output_buffer = '',
                   process_id = NULL,
                   process_start_time = NULL,
                   remote_pid = NULL,
                   execution_id = NULL,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE monitoring_job_id = :jobId
               AND status IN ('IDLE', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'STOPPED')
            """, nativeQuery = true)
    int claim(@Param("jobId") Long jobId,
              @Param("owner") String owner,
              @Param("startedBy") String startedBy,
              @Param("maxRuntimeSeconds") int maxRuntimeSeconds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = 'RUNNING',
                   process_id = :processId,
                   process_start_time = :processStartTime,
                   execution_id = :executionId,
                   heartbeat_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND owner_instance_id = :owner
               AND status = 'STARTING'
            """, nativeQuery = true)
    int markRunning(@Param("jobId") Long jobId, @Param("owner") String owner,
                    @Param("processId") long processId,
                    @Param("processStartTime") java.time.OffsetDateTime processStartTime,
                    @Param("executionId") Long executionId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET remote_pid = :remotePid,
                   heartbeat_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND owner_instance_id = :owner
               AND status IN ('STARTING', 'RUNNING', 'STOPPING')
            """, nativeQuery = true)
    int setRemotePid(@Param("jobId") Long jobId, @Param("owner") String owner,
                     @Param("remotePid") String remotePid);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET output_buffer = RIGHT(COALESCE(output_buffer, '') || :chunk, 65536),
                   heartbeat_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND owner_instance_id = :owner
               AND status IN ('STARTING', 'RUNNING', 'STOPPING')
            """, nativeQuery = true)
    int appendOutput(@Param("jobId") Long jobId, @Param("owner") String owner, @Param("chunk") String chunk);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = :status,
                   finished_at = CURRENT_TIMESTAMP,
                   heartbeat_at = CURRENT_TIMESTAMP,
                   exit_code = :exitCode,
                   error_message = :errorMessage,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND owner_instance_id = :owner
               AND status IN ('STARTING', 'RUNNING', 'STOPPING')
            """, nativeQuery = true)
    int finish(@Param("jobId") Long jobId, @Param("owner") String owner,
               @Param("status") String status, @Param("exitCode") Integer exitCode,
               @Param("errorMessage") String errorMessage);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = 'STOPPING',
                   heartbeat_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND status IN ('STARTING', 'RUNNING')
            """, nativeQuery = true)
    int requestStop(@Param("jobId") Long jobId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = :status,
                   finished_at = CURRENT_TIMESTAMP,
                   heartbeat_at = CURRENT_TIMESTAMP,
                   error_message = :reason,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND status IN ('STARTING', 'RUNNING', 'STOPPING', 'RECOVERY_REQUIRED')
            """, nativeQuery = true)
    int finishRecovery(@Param("jobId") Long jobId, @Param("status") String status,
                       @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.streaming_job_claims
               SET status = 'RECOVERY_REQUIRED',
                   error_message = :reason,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE monitoring_job_id = :jobId
               AND status IN ('STARTING', 'RUNNING', 'STOPPING')
            """, nativeQuery = true)
    int markRecoveryRequired(@Param("jobId") Long jobId, @Param("reason") String reason);
}
