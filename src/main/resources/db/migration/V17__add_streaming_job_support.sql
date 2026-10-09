-- Streaming execution mode and shared, database-backed execution claims.
ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS max_streaming_runtime_seconds INTEGER NOT NULL DEFAULT 300;

DO $$
BEGIN
    ALTER TABLE ra_fcb.monitoring_jobs
        ADD CONSTRAINT chk_monitoring_jobs_execution_mode
        CHECK (execution_mode IN ('STANDARD', 'STREAMING'));
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE ra_fcb.monitoring_jobs
        ADD CONSTRAINT chk_monitoring_jobs_streaming_runtime
        CHECK (max_streaming_runtime_seconds BETWEEN 1 AND 86400);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- The streaming service uses these fields for atomic cross-instance claims and
-- process recovery. Output remains in a bounded in-memory buffer, not in history.
CREATE TABLE IF NOT EXISTS ra_fcb.streaming_job_claims (
    id BIGSERIAL PRIMARY KEY,
    monitoring_job_id BIGINT NOT NULL UNIQUE
        REFERENCES ra_fcb.monitoring_jobs(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    started_by BIGINT,
    claim_owner VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    process_id BIGINT,
    process_host VARCHAR(255),
    process_marker TEXT,
    remote_log_path TEXT,
    terminal_message TEXT,
    CONSTRAINT chk_streaming_job_claim_status
        CHECK (status IN ('IDLE', 'STARTING', 'RUNNING', 'STOPPING',
                          'COMPLETED', 'STOPPED', 'FAILED', 'TIMED_OUT',
                          'RECOVERY_REQUIRED'))
);

CREATE INDEX IF NOT EXISTS idx_streaming_job_claims_status
    ON ra_fcb.streaming_job_claims(status);

CREATE INDEX IF NOT EXISTS idx_streaming_job_claims_heartbeat
    ON ra_fcb.streaming_job_claims(heartbeat_at);

INSERT INTO ra_fcb.streaming_job_claims (monitoring_job_id)
SELECT id
FROM ra_fcb.monitoring_jobs
WHERE execution_mode = 'STREAMING'
ON CONFLICT (monitoring_job_id) DO NOTHING;
