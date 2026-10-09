-- Streaming execution mode and durable cross-instance claim state.
-- Streaming output is transient in application memory; it is not persisted as
-- monitoring_results or standard monitoring execution history.

ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN max_streaming_runtime_seconds INTEGER NOT NULL DEFAULT 300;

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT chk_monitoring_jobs_execution_mode
        CHECK (execution_mode IN ('STANDARD', 'STREAMING')),
    ADD CONSTRAINT chk_monitoring_jobs_streaming_runtime
        CHECK (max_streaming_runtime_seconds > 0);

CREATE TABLE ra_fcb.streaming_job_claims (
    monitoring_job_id BIGINT PRIMARY KEY
        REFERENCES ra_fcb.monitoring_jobs(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    started_by BIGINT,
    claim_owner VARCHAR(200),
    started_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    heartbeat_at TIMESTAMPTZ,
    process_id BIGINT,
    process_host VARCHAR(255),
    process_marker TEXT,
    remote_log_path TEXT,
    terminal_message TEXT,
    CONSTRAINT chk_streaming_job_claim_status CHECK (
        status IN ('IDLE', 'STARTING', 'RUNNING', 'STOPPING',
                   'RECOVERY_REQUIRED', 'COMPLETED', 'FAILED',
                   'TIMED_OUT', 'STOPPED')
    )
);

CREATE INDEX idx_streaming_job_claims_status
    ON ra_fcb.streaming_job_claims(status);

CREATE INDEX idx_streaming_job_claims_owner
    ON ra_fcb.streaming_job_claims(claim_owner);

-- Seed claim rows for existing jobs. New jobs are initialized on first use.
INSERT INTO ra_fcb.streaming_job_claims (monitoring_job_id, status)
SELECT id, 'IDLE'
FROM ra_fcb.monitoring_jobs
ON CONFLICT (monitoring_job_id) DO NOTHING;
