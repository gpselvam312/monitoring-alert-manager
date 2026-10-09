-- Streaming jobs are a mode of existing monitoring jobs, not a separate job framework.
ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN max_streaming_runtime_seconds INTEGER NOT NULL DEFAULT 300;

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT chk_monitoring_jobs_execution_mode
        CHECK (execution_mode IN ('STANDARD', 'STREAMING')),
    ADD CONSTRAINT chk_monitoring_jobs_streaming_runtime
        CHECK (max_streaming_runtime_seconds BETWEEN 1 AND 86400);

-- Durable atomic claims prevent duplicate starts across application instances.
-- Output itself remains transient and is not written to monitoring history.
CREATE TABLE ra_fcb.streaming_job_claims (
    monitoring_job_id BIGINT PRIMARY KEY REFERENCES ra_fcb.monitoring_jobs(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL DEFAULT 'IDLE'
        CHECK (status IN ('IDLE', 'STARTING', 'RUNNING', 'STOPPING', 'RECOVERY_REQUIRED',
                          'COMPLETED', 'STOPPED', 'FAILED', 'TIMED_OUT')),
    started_by BIGINT REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    claim_owner VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    process_id BIGINT,
    process_host VARCHAR(255),
    process_marker VARCHAR(1000),
    remote_log_path VARCHAR(1000),
    terminal_message TEXT
);
CREATE INDEX idx_streaming_job_claims_status ON ra_fcb.streaming_job_claims(status);
CREATE INDEX idx_streaming_job_claims_started_at ON ra_fcb.streaming_job_claims(started_at DESC);
