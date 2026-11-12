-- Durable execution-to-alert-evaluation handoff. The request is inserted in the
-- same transaction as execution/result persistence so a process restart cannot
-- silently lose an evaluation request.

CREATE TABLE ra_fcb.alert_evaluation_requests (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_evaluation_requests_execution
        FOREIGN KEY (execution_id) REFERENCES ra_fcb.monitoring_executions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_alert_evaluation_requests_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_alert_evaluation_requests_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_alert_evaluation_requests_due
    ON ra_fcb.alert_evaluation_requests (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_alert_evaluation_requests_stale
    ON ra_fcb.alert_evaluation_requests (locked_at)
    WHERE status = 'PROCESSING';
