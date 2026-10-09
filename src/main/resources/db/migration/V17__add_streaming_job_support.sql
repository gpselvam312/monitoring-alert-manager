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

CREATE TABLE IF NOT EXISTS ra_fcb.streaming_job_claims (
    id BIGSERIAL PRIMARY KEY,
    monitoring_job_id BIGINT NOT NULL UNIQUE
        REFERENCES ra_fcb.monitoring_jobs(id) ON DELETE CASCADE,
    execution_id BIGINT
        REFERENCES ra_fcb.monitoring_executions(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    owner_instance_id VARCHAR(100),
    process_id BIGINT,
    process_start_time TIMESTAMP WITH TIME ZONE,
    remote_pid VARCHAR(100),
    started_by VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    deadline_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    exit_code INTEGER,
    error_message TEXT,
    output_buffer TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_streaming_job_claim_status
        CHECK (status IN ('IDLE', 'STARTING', 'RUNNING', 'STOPPING',
                          'COMPLETED', 'FAILED', 'TIMED_OUT', 'STOPPED',
                          'RECOVERY_REQUIRED'))
);

CREATE INDEX IF NOT EXISTS idx_streaming_job_claims_status
    ON ra_fcb.streaming_job_claims(status);

CREATE INDEX IF NOT EXISTS idx_streaming_job_claims_deadline
    ON ra_fcb.streaming_job_claims(deadline_at);

INSERT INTO ra_fcb.streaming_job_claims (monitoring_job_id)
SELECT id
FROM ra_fcb.monitoring_jobs
ON CONFLICT (monitoring_job_id) DO NOTHING;
