-- Teams channels already contain their destination (for example, a configured webhook).
-- Recipient and group targets remain mandatory for Email and WhatsApp at service-validation level.
ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP CONSTRAINT chk_alert_rule_notification_routes_target;

ALTER TABLE ra_fcb.alert_rule_notification_routes
    ADD CONSTRAINT chk_alert_rule_notification_routes_target
    CHECK (recipient_id IS NULL OR group_id IS NULL);
