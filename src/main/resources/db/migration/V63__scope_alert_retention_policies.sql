-- Scope retention policies by application, environment and alert source type.
ALTER TABLE ra_fcb.alerts
    ADD COLUMN alert_type VARCHAR(40) NOT NULL DEFAULT 'MONITORING_JOB';
ALTER TABLE ra_fcb.alerts
    ADD CONSTRAINT chk_alerts_alert_type
    CHECK (length(btrim(alert_type)) > 0);

ALTER TABLE ra_fcb.alert_retention_policies
    DROP CONSTRAINT uq_alert_retention_policy_data_type,
    ADD COLUMN application_id BIGINT,
    ADD COLUMN environment_id BIGINT,
    ADD COLUMN alert_type VARCHAR(40) NOT NULL DEFAULT 'ALL',
    ADD CONSTRAINT fk_alert_retention_policy_application
        FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_alert_retention_policy_environment
        FOREIGN KEY (environment_id) REFERENCES ra_fcb.environments(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_alert_retention_policy_alert_type
        CHECK (length(btrim(alert_type)) > 0),
    ADD CONSTRAINT chk_alert_retention_policy_environment_scope
        CHECK (environment_id IS NULL OR application_id IS NOT NULL);

CREATE UNIQUE INDEX uq_alert_retention_policy_scope
    ON ra_fcb.alert_retention_policies (
        data_type,
        COALESCE(application_id, 0),
        COALESCE(environment_id, 0),
        alert_type
    );
CREATE INDEX idx_alert_retention_policy_scope
    ON ra_fcb.alert_retention_policies (application_id, environment_id, alert_type, enabled);
CREATE INDEX idx_alerts_type_scope
    ON ra_fcb.alerts (alert_type, monitoring_job_id);
