-- Approved cleanup of obsolete schema objects after the V1-V71 source review.
-- This migration intentionally fails closed if current relationship data is incomplete.
-- No CASCADE is used: unexpected foreign-key dependencies must stop the migration.

DO $$
DECLARE
    missing_recipient_links BIGINT;
    missing_group_links BIGINT;
    memberships_without_role BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO missing_recipient_links
      FROM ra_fcb.alert_rule_notification_routes r
     WHERE r.recipient_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM ra_fcb.alert_rule_notification_route_recipients x
            WHERE x.route_id = r.id
              AND x.recipient_id = r.recipient_id
       );

    SELECT COUNT(*)
      INTO missing_group_links
      FROM ra_fcb.alert_rule_notification_routes r
     WHERE r.group_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM ra_fcb.alert_rule_notification_route_groups x
            WHERE x.route_id = r.id
              AND x.group_id = r.group_id
       );

    SELECT COUNT(*)
      INTO memberships_without_role
      FROM ra_fcb.user_applications ua
     WHERE NOT EXISTS (
           SELECT 1
             FROM ra_fcb.user_application_roles uar
            WHERE uar.user_id = ua.user_id
              AND uar.application_id = ua.application_id
       );

    IF missing_recipient_links > 0
       OR missing_group_links > 0
       OR memberships_without_role > 0 THEN
        RAISE EXCEPTION
            'V72 preflight failed: missing recipient links=%, missing group links=%, memberships without role assignments=%',
            missing_recipient_links, missing_group_links, memberships_without_role;
    END IF;
END
$$;

-- Remove old scalar route targets after confirming their values are represented
-- in the current many-to-many target tables.
ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP CONSTRAINT fk_alert_rule_notification_routes_recipient;
ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP CONSTRAINT fk_alert_rule_notification_routes_group;
ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP CONSTRAINT chk_alert_rule_notification_routes_target;
ALTER TABLE ra_fcb.alert_rule_notification_routes
    DROP COLUMN recipient_id,
    DROP COLUMN group_id;

-- The old membership table was replaced by user_application_roles in V25.
DROP TABLE ra_fcb.user_applications;

-- Drop the preserved V1 alert/notification model in dependency order.
-- Deliberately omit CASCADE so unexpected external dependencies stop the migration.
DROP TABLE ra_fcb.legacy_notification_history;
DROP TABLE ra_fcb.legacy_monitor_notification_channels;
DROP TABLE ra_fcb.legacy_monitor_recipients;
DROP TABLE ra_fcb.legacy_alert_rules;
DROP TABLE ra_fcb.legacy_alert_states;
DROP TABLE ra_fcb.legacy_alert_history;
DROP TABLE ra_fcb.legacy_notification_channels;
DROP TABLE ra_fcb.legacy_recipients;

-- These V1 tables were superseded and have no active application mapping/workflow.
DROP TABLE ra_fcb.monitor_execution_history;
DROP TABLE ra_fcb.audit_log;
