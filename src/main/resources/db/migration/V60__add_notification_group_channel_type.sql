ALTER TABLE ra_fcb.notification_groups
    ADD COLUMN channel_type VARCHAR(20) NOT NULL DEFAULT 'EMAIL';

ALTER TABLE ra_fcb.notification_groups
    ADD CONSTRAINT ck_notification_groups_channel_type
    CHECK (channel_type IN ('EMAIL', 'WHATSAPP'));

CREATE INDEX ix_notification_groups_channel_enabled_name
    ON ra_fcb.notification_groups (channel_type, enabled, name);
