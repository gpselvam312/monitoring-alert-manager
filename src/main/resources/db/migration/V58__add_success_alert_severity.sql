-- Extend the configurable alert severity model with successful/healthy notifications.
ALTER TABLE ra_fcb.alert_rules
    DROP CONSTRAINT chk_alert_rules_severity;
ALTER TABLE ra_fcb.alert_rules
    ADD CONSTRAINT chk_alert_rules_severity
    CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS'));

ALTER TABLE ra_fcb.alerts
    DROP CONSTRAINT chk_alerts_severity;
ALTER TABLE ra_fcb.alerts
    ADD CONSTRAINT chk_alerts_severity
    CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS'));

ALTER TABLE ra_fcb.alert_history
    DROP CONSTRAINT chk_alert_history_previous_severity;
ALTER TABLE ra_fcb.alert_history
    ADD CONSTRAINT chk_alert_history_previous_severity
    CHECK (previous_severity IS NULL OR previous_severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS'));

ALTER TABLE ra_fcb.alert_history
    DROP CONSTRAINT chk_alert_history_new_severity;
ALTER TABLE ra_fcb.alert_history
    ADD CONSTRAINT chk_alert_history_new_severity
    CHECK (new_severity IS NULL OR new_severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS'));

ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP CONSTRAINT chk_alert_rule_notification_routes_severity;
ALTER TABLE ra_fcb.alert_rule_notification_routes
    ADD CONSTRAINT chk_alert_rule_notification_routes_severity
    CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS'));
