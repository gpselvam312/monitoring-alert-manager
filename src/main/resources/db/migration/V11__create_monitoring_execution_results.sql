-- ============================================================
-- MONITORING EXECUTIONS
-- Stores every execution of a monitoring job.
-- ============================================================

CREATE TABLE monitoring_executions (
    id                  BIGSERIAL PRIMARY KEY,
    monitoring_job_id   BIGINT NOT NULL,

    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,

    status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING',

    duration_ms         BIGINT,
    attempt_number      INTEGER NOT NULL DEFAULT 1,

    error_message       TEXT,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_monitoring_executions_job
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id),

    CONSTRAINT chk_monitoring_execution_status
        CHECK (
            status IN (
                'RUNNING',
                'SUCCESS',
                'FAILED',
                'TIMEOUT',
                'ERROR'
            )
        ),

    CONSTRAINT chk_monitoring_execution_attempt
        CHECK (attempt_number > 0),

    CONSTRAINT chk_monitoring_execution_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0)
);


-- ============================================================
-- MONITORING RESULTS
-- Stores the structured result produced by an execution.
-- ============================================================

CREATE TABLE monitoring_results (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        BIGINT NOT NULL,

    result_type         VARCHAR(20) NOT NULL,
    status              VARCHAR(20),

    value               VARCHAR(500),
    unit                VARCHAR(50),
    message             TEXT,

    result_data         JSONB,
    raw_output          TEXT,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_monitoring_results_execution
        FOREIGN KEY (execution_id)
        REFERENCES monitoring_executions(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_monitoring_result_type
        CHECK (
            result_type IN (
                'STATUS',
                'VALUE',
                'METRICS',
                'TABLE',
                'TEXT'
            )
        ),

    CONSTRAINT chk_monitoring_result_status
        CHECK (
            status IS NULL
            OR status IN (
                'OK',
                'WARNING',
                'FAILED'
            )
        )
);


-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX idx_monitoring_executions_job
    ON monitoring_executions(monitoring_job_id);

CREATE INDEX idx_monitoring_executions_started_at
    ON monitoring_executions(started_at);

CREATE INDEX idx_monitoring_executions_status
    ON monitoring_executions(status);

CREATE INDEX idx_monitoring_results_execution
    ON monitoring_results(execution_id);