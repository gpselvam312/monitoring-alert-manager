-- Record how each monitoring execution was initiated.
-- Historical executions are intentionally UNKNOWN; their trigger source cannot
-- be reliably inferred from their timestamps or result values.

ALTER TABLE ra_fcb.monitoring_executions
    ADD COLUMN trigger_source VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE ra_fcb.monitoring_executions
    ADD CONSTRAINT chk_monitoring_execution_trigger_source
    CHECK (trigger_source IN ('SCHEDULED', 'MANUAL', 'UNKNOWN'));

CREATE INDEX idx_monitoring_executions_source_started
    ON ra_fcb.monitoring_executions (trigger_source, started_at DESC);
