-- Table-driven alert source types used by alert lifecycle and retention configuration.
CREATE TABLE ra_fcb.alert_types (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alert_types_name UNIQUE (name),
    CONSTRAINT chk_alert_types_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT chk_alert_types_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_alert_types_sort_order CHECK (sort_order >= 0)
);

INSERT INTO ra_fcb.alert_types (code, name, description, enabled, sort_order)
VALUES ('MONITORING_JOB', 'Monitoring Jobs', 'Alerts generated from monitoring job execution and evaluation.', TRUE, 10);

ALTER TABLE ra_fcb.alerts
    ADD CONSTRAINT fk_alerts_alert_type
    FOREIGN KEY (alert_type) REFERENCES ra_fcb.alert_types(code) ON DELETE RESTRICT;

CREATE INDEX idx_alert_types_enabled_sort
    ON ra_fcb.alert_types (enabled, sort_order, name);
