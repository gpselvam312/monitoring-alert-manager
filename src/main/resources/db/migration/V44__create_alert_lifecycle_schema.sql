-- The initial schema contains an unused, legacy alert/notification model.
-- Preserve its data and relationships under explicit legacy names before
-- creating the lifecycle model used by the new Alerts feature.
ALTER TABLE ra_fcb.alert_rules RENAME TO legacy_alert_rules;
ALTER TABLE ra_fcb.alert_states RENAME TO legacy_alert_states;
ALTER TABLE ra_fcb.alert_history RENAME TO legacy_alert_history;
ALTER TABLE ra_fcb.notification_history RENAME TO legacy_notification_history;
ALTER TABLE ra_fcb.notification_channels RENAME TO legacy_notification_channels;
ALTER TABLE ra_fcb.recipients RENAME TO legacy_recipients;
ALTER TABLE ra_fcb.monitor_notification_channels RENAME TO legacy_monitor_notification_channels;
ALTER TABLE ra_fcb.monitor_recipients RENAME TO legacy_monitor_recipients;

-- Renaming a SERIAL-backed table does not rename its sequence. Move the
-- colliding legacy sequences before creating the replacement tables.
ALTER SEQUENCE ra_fcb.alert_rules_id_seq RENAME TO legacy_alert_rules_id_seq;
ALTER SEQUENCE ra_fcb.alert_history_id_seq RENAME TO legacy_alert_history_id_seq;
ALTER SEQUENCE ra_fcb.notification_channels_id_seq RENAME TO legacy_notification_channels_id_seq;

COMMENT ON TABLE ra_fcb.legacy_alert_rules IS
    'Preserved legacy alert rule schema; superseded by alert_rules.';
COMMENT ON TABLE ra_fcb.legacy_alert_history IS
    'Preserved legacy alert history schema; superseded by alert_history.';
COMMENT ON TABLE ra_fcb.legacy_notification_history IS
    'Preserved legacy notification history schema; superseded by notification_deliveries.';

CREATE TABLE ra_fcb.alert_rules (
    id BIGSERIAL,
    CONSTRAINT pk_alert_rules_v2 PRIMARY KEY (id),
    monitoring_job_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    severity VARCHAR(20) NOT NULL DEFAULT 'CRITICAL',
    execution_source_policy VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED_ONLY',
    condition_config JSONB NOT NULL,
    recovery_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    notification_policy_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_alert_rules_monitoring_job
        FOREIGN KEY (monitoring_job_id)
        REFERENCES ra_fcb.monitoring_jobs(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_alert_rules_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_alert_rules_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT uq_alert_rules_id_job UNIQUE (id, monitoring_job_id),
    CONSTRAINT uq_alert_rules_job_name UNIQUE (monitoring_job_id, name),
    CONSTRAINT chk_alert_rules_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_rules_execution_source
        CHECK (execution_source_policy IN ('SCHEDULED_ONLY', 'MANUAL_ONLY', 'ALL_NON_STREAMING')),
    CONSTRAINT chk_alert_rules_condition_object
        CHECK (jsonb_typeof(condition_config) = 'object'),
    CONSTRAINT chk_alert_rules_recovery_object
        CHECK (jsonb_typeof(recovery_config) = 'object')
);

CREATE INDEX idx_alert_rules_enabled_job
    ON ra_fcb.alert_rules (monitoring_job_id, enabled);

CREATE TABLE ra_fcb.alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT NOT NULL,
    monitoring_job_id BIGINT NOT NULL,
    correlation_key VARCHAR(500) NOT NULL DEFAULT 'DEFAULT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    current_value JSONB,
    first_occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    last_execution_id BIGINT NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alerts_rule_job
        FOREIGN KEY (alert_rule_id, monitoring_job_id)
        REFERENCES ra_fcb.alert_rules(id, monitoring_job_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_alerts_last_execution
        FOREIGN KEY (last_execution_id)
        REFERENCES ra_fcb.monitoring_executions(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_alerts_status
        CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT chk_alerts_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alerts_occurrence_count
        CHECK (occurrence_count > 0),
    CONSTRAINT chk_alerts_resolution_timestamp
        CHECK ((status = 'ACTIVE' AND resolved_at IS NULL)
            OR (status = 'RESOLVED' AND resolved_at IS NOT NULL)),
    CONSTRAINT chk_alerts_correlation_key
        CHECK (length(btrim(correlation_key)) > 0)
);

-- A rule/correlation key can have only one active lifecycle at a time.
-- A later recurrence after resolution creates a new alert row and ID.
CREATE UNIQUE INDEX uq_alerts_one_active_correlation
    ON ra_fcb.alerts (alert_rule_id, correlation_key)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_alerts_status_severity_updated
    ON ra_fcb.alerts (status, severity, updated_at DESC);
CREATE INDEX idx_alerts_job_status_updated
    ON ra_fcb.alerts (monitoring_job_id, status, updated_at DESC);
CREATE INDEX idx_alerts_history_order
    ON ra_fcb.alerts (last_occurred_at DESC, id DESC);

CREATE TABLE ra_fcb.alert_history (
    id BIGSERIAL,
    CONSTRAINT pk_alert_history_v2 PRIMARY KEY (id),
    alert_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    previous_severity VARCHAR(20),
    new_severity VARCHAR(20),
    execution_id BIGINT,
    observed_value JSONB,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_history_alert
        FOREIGN KEY (alert_id) REFERENCES ra_fcb.alerts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_history_execution
        FOREIGN KEY (execution_id) REFERENCES ra_fcb.monitoring_executions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_alert_history_event_type
        CHECK (event_type IN (
            'CREATED', 'OCCURRENCE', 'SEVERITY_CHANGED', 'RESOLVED',
            'NOTIFICATION_QUEUED', 'NOTIFICATION_RESULT', 'RULE_EVALUATION_ERROR'
        )),
    CONSTRAINT chk_alert_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT chk_alert_history_new_status
        CHECK (new_status IS NULL OR new_status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT chk_alert_history_previous_severity
        CHECK (previous_severity IS NULL OR previous_severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_history_new_severity
        CHECK (new_severity IS NULL OR new_severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_history_details_object
        CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_alert_history_alert_time
    ON ra_fcb.alert_history (alert_id, occurred_at DESC, id DESC);
CREATE INDEX idx_alert_history_execution
    ON ra_fcb.alert_history (execution_id)
    WHERE execution_id IS NOT NULL;

-- Durable idempotency ledger: each rule/execution pair is evaluated at most once.
CREATE TABLE ra_fcb.alert_rule_evaluations (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_rule_evaluations_rule
        FOREIGN KEY (alert_rule_id) REFERENCES ra_fcb.alert_rules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_rule_evaluations_execution
        FOREIGN KEY (execution_id) REFERENCES ra_fcb.monitoring_executions(id) ON DELETE RESTRICT,
    CONSTRAINT uq_alert_rule_evaluations_rule_execution UNIQUE (alert_rule_id, execution_id),
    CONSTRAINT chk_alert_rule_evaluations_outcome
        CHECK (outcome IN ('MATCHED', 'NO_MATCH', 'SKIPPED', 'ERROR')),
    CONSTRAINT chk_alert_rule_evaluations_details_object
        CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_alert_rule_evaluations_execution
    ON ra_fcb.alert_rule_evaluations (execution_id);

CREATE TABLE ra_fcb.user_alert_states (
    user_id BIGINT NOT NULL,
    alert_id BIGINT NOT NULL,
    read_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_alert_states PRIMARY KEY (user_id, alert_id),
    CONSTRAINT fk_user_alert_states_user
        FOREIGN KEY (user_id) REFERENCES ra_fcb.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_alert_states_alert
        FOREIGN KEY (alert_id) REFERENCES ra_fcb.alerts(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_alert_states_unread
    ON ra_fcb.user_alert_states (user_id, alert_id)
    WHERE read_at IS NULL;
