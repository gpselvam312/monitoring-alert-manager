-- Retention is opt-in. No policy rows are seeded, so cleanup cannot run
-- until an administrator explicitly configures a policy.

CREATE TABLE ra_fcb.alert_retention_policies (
    id BIGSERIAL PRIMARY KEY,
    data_type VARCHAR(40) NOT NULL,
    retention_days INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uq_alert_retention_policy_data_type UNIQUE (data_type),
    CONSTRAINT chk_alert_retention_policy_data_type
        CHECK (data_type IN ('RESOLVED_ALERTS', 'ALERT_HISTORY', 'NOTIFICATION_DELIVERIES', 'RULE_EVALUATIONS')),
    CONSTRAINT chk_alert_retention_policy_days
        CHECK (retention_days > 0),
    CONSTRAINT fk_alert_retention_policies_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_alert_retention_policies_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL
);

CREATE INDEX idx_alert_retention_policies_enabled
    ON ra_fcb.alert_retention_policies (enabled, data_type);

CREATE TABLE ra_fcb.alert_cleanup_runs (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    records_examined BIGINT NOT NULL DEFAULT 0,
    records_deleted BIGINT NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    requested_by BIGINT,
    CONSTRAINT fk_alert_cleanup_runs_policy
        FOREIGN KEY (policy_id) REFERENCES ra_fcb.alert_retention_policies(id) ON DELETE SET NULL,
    CONSTRAINT fk_alert_cleanup_runs_user
        FOREIGN KEY (requested_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_alert_cleanup_runs_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_alert_cleanup_runs_counts
        CHECK (records_examined >= 0 AND records_deleted >= 0 AND records_deleted <= records_examined),
    CONSTRAINT chk_alert_cleanup_runs_details_object
        CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_alert_cleanup_runs_started
    ON ra_fcb.alert_cleanup_runs (started_at DESC);
CREATE INDEX idx_alert_cleanup_runs_policy_status
    ON ra_fcb.alert_cleanup_runs (policy_id, status, started_at DESC);
