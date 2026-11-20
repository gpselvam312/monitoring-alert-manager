-- A bounded lease prevents duplicate non-concurrent job executions across application instances.
ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN execution_claim_token UUID,
    ADD COLUMN execution_claim_until TIMESTAMPTZ;

CREATE INDEX idx_monitoring_jobs_execution_claim_until
    ON ra_fcb.monitoring_jobs (execution_claim_until)
    WHERE execution_claim_token IS NOT NULL;
