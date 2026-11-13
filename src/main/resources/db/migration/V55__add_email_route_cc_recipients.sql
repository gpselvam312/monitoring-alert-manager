-- CC recipients are scoped to an individual alert rule route.
ALTER TABLE ra_fcb.alert_rule_notification_routes
    ADD COLUMN cc_recipients TEXT;

-- Snapshot CC recipients at queue time so retries use the original route configuration.
ALTER TABLE ra_fcb.notification_deliveries
    ADD COLUMN cc_recipients_snapshot TEXT;
