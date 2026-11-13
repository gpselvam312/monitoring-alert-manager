-- Allow a rule route to target multiple individual recipients and multiple groups.
-- Keep the legacy recipient_id/group_id columns for existing installations and compatibility.
CREATE TABLE ra_fcb.alert_rule_notification_route_recipients (
    route_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    CONSTRAINT pk_alert_rule_route_recipients PRIMARY KEY (route_id, recipient_id),
    CONSTRAINT fk_alert_rule_route_recipients_route
        FOREIGN KEY (route_id) REFERENCES ra_fcb.alert_rule_notification_routes(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_rule_route_recipients_recipient
        FOREIGN KEY (recipient_id) REFERENCES ra_fcb.notification_recipients(id) ON DELETE RESTRICT
);

CREATE TABLE ra_fcb.alert_rule_notification_route_groups (
    route_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    CONSTRAINT pk_alert_rule_route_groups PRIMARY KEY (route_id, group_id),
    CONSTRAINT fk_alert_rule_route_groups_route
        FOREIGN KEY (route_id) REFERENCES ra_fcb.alert_rule_notification_routes(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_rule_route_groups_group
        FOREIGN KEY (group_id) REFERENCES ra_fcb.notification_groups(id) ON DELETE RESTRICT
);

CREATE INDEX idx_alert_rule_route_recipients_recipient
    ON ra_fcb.alert_rule_notification_route_recipients(recipient_id);
CREATE INDEX idx_alert_rule_route_groups_group
    ON ra_fcb.alert_rule_notification_route_groups(group_id);

-- Migrate all existing single-recipient and single-group routes without changing delivery behavior.
INSERT INTO ra_fcb.alert_rule_notification_route_recipients(route_id, recipient_id)
SELECT id, recipient_id
FROM ra_fcb.alert_rule_notification_routes
WHERE recipient_id IS NOT NULL;

INSERT INTO ra_fcb.alert_rule_notification_route_groups(route_id, group_id)
SELECT id, group_id
FROM ra_fcb.alert_rule_notification_routes
WHERE group_id IS NOT NULL;
