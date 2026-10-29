-- Rule-specific delivery routes keep recipients and event preferences scoped to one alert rule.
-- Existing policy routes remain available for backward compatibility and are copied per rule.
CREATE TABLE ra_fcb.alert_rule_notification_routes (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    recipient_id BIGINT,
    group_id BIGINT,
    severity VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_interval_seconds INTEGER NOT NULL DEFAULT 300,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_rule_notification_routes_rule
        FOREIGN KEY (alert_rule_id) REFERENCES ra_fcb.alert_rules(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_rule_notification_routes_channel
        FOREIGN KEY (channel_id) REFERENCES ra_fcb.notification_channels(id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_rule_notification_routes_recipient
        FOREIGN KEY (recipient_id) REFERENCES ra_fcb.notification_recipients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_rule_notification_routes_group
        FOREIGN KEY (group_id) REFERENCES ra_fcb.notification_groups(id) ON DELETE RESTRICT,
    CONSTRAINT chk_alert_rule_notification_routes_target
        CHECK ((recipient_id IS NOT NULL AND group_id IS NULL)
            OR (recipient_id IS NULL AND group_id IS NOT NULL)),
    CONSTRAINT chk_alert_rule_notification_routes_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_rule_notification_routes_event
        CHECK (event_type IN ('CREATED', 'RECURRENCE', 'ESCALATED', 'RECOVERED', 'REMINDER')),
    CONSTRAINT chk_alert_rule_notification_routes_interval
        CHECK (minimum_interval_seconds >= 0)
);

CREATE INDEX idx_alert_rule_notification_routes_match
    ON ra_fcb.alert_rule_notification_routes (alert_rule_id, enabled, severity, event_type);
CREATE INDEX idx_alert_rule_notification_routes_channel
    ON ra_fcb.alert_rule_notification_routes (channel_id, enabled);

-- Preserve current delivery behaviour by copying policy routes onto every rule using that policy.
INSERT INTO ra_fcb.alert_rule_notification_routes
    (alert_rule_id, channel_id, recipient_id, group_id, severity, event_type, enabled,
     minimum_interval_seconds, created_at, updated_at)
SELECT ar.id, nr.channel_id, nr.recipient_id, nr.group_id, nr.severity, nr.event_type, nr.enabled,
       CASE WHEN nr.event_type IN ('RECURRENCE', 'REMINDER') AND nr.minimum_interval_seconds = 0
            THEN 300 ELSE nr.minimum_interval_seconds END,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ra_fcb.alert_rules ar
JOIN ra_fcb.notification_routes nr ON nr.notification_policy_id = ar.notification_policy_id
WHERE ar.notification_policy_id IS NOT NULL;
