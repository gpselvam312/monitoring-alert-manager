-- Streaming execution metadata and cross-instance claim state.
-- Streaming output is transient and is deleted when a run ends; it is not stored
-- in monitoring_results or exposed as standard monitoring history.

ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN max_streaming_runtime_seconds INTEGER NOT NULL DEFAULT 300;

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT chk_monitoring_jobs_execution_mode
        CHECK (execution_mode IN ('STANDARD', 'STREAMING')),
    ADD CONSTRAINT chk_monitoring_jobs_streaming_runtime
        CHECK (max_streaming_runtime_seconds > 0);

CREATE TABLE ra_fcb.streaming_job_claims (
    job_id BIGINT PRIMARY KEY
        REFERENCES ra_fcb.monitoring_jobs(id) ON DELETE CASCADE,
    state VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    owner_instance VARCHAR(200),
    process_id BIGINT,
    remote_pid VARCHAR(100),
    started_by BIGINT,
    started_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stop_requested BOOLEAN NOT NULL DEFAULT FALSE,
    exit_code INTEGER,
    error_message TEXT,
    run_token UUID,
    CONSTRAINT chk_streaming_job_claim_state CHECK (
        state IN ('IDLE', 'STARTING', 'RUNNING', 'STOPPING',
                  'RECOVERY_REQUIRED', 'COMPLETED', 'FAILED',
                  'TIMED_OUT', 'STOPPED')
    )
);

CREATE TABLE ra_fcb.streaming_job_output_chunks (
    job_id BIGINT NOT NULL
        REFERENCES ra_fcb.streaming_job_claims(job_id) ON DELETE CASCADE,
    sequence_no BIGSERIAL NOT NULL,
    output_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id, sequence_no)
);

CREATE INDEX idx_streaming_output_job_sequence
    ON ra_fcb.streaming_job_output_chunks(job_id, sequence_no);

-- Create an IDLE claim row for every existing job so claim updates can be atomic.
INSERT INTO ra_fcb.streaming_job_claims (job_id, state)
SELECT id, 'IDLE'
FROM ra_fcb.monitoring_jobs
ON CONFLICT (job_id) DO NOTHING;
