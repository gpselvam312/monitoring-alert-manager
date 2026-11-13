-- Versioned, channel-neutral presentation configuration for alert notifications.
ALTER TABLE ra_fcb.alert_rules
    ADD COLUMN presentation_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN config_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE ra_fcb.alert_rules
    ADD CONSTRAINT chk_alert_rules_config_version
    CHECK (config_version >= 1);

COMMENT ON COLUMN ra_fcb.alert_rules.presentation_config IS
    'Versioned channel-neutral alert presentation sections; empty object uses legacy subject/message templates.';
COMMENT ON COLUMN ra_fcb.alert_rules.config_version IS
    'Version of the alert rule configuration contract.';
