-- Notification delivery is independent of alert state transitions. Configuration
-- contains non-secret settings; credential_reference points to a protected
-- application/system configuration entry rather than storing credentials here.

CREATE TABLE ra_fcb.notification_channels (
    id BIGSERIAL,
    CONSTRAINT pk_notification_channels_v2 PRIMARY KEY (id),
    name VARCHAR(100) NOT NULL,
    channel_type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    credential_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uq_notification_channels_name_v2 UNIQUE (name),
    CONSTRAINT chk_notification_channels_type_v2
        CHECK (channel_type IN ('EMAIL', 'TEAMS', 'WHATSAPP')),
    CONSTRAINT chk_notification_channels_configuration
        CHECK (jsonb_typeof(configuration) = 'object'),
    CONSTRAINT fk_notification_channels_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_channels_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL
);

CREATE INDEX idx_notification_channels_enabled_type
    ON ra_fcb.notification_channels (enabled, channel_type);

CREATE TABLE ra_fcb.notification_recipients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    user_id BIGINT,
    email VARCHAR(255),
    phone_number VARCHAR(50),
    teams_address VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_notification_recipients_user
        FOREIGN KEY (user_id) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_recipients_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_recipients_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_notification_recipients_destination
        CHECK (email IS NOT NULL OR phone_number IS NOT NULL OR teams_address IS NOT NULL OR user_id IS NOT NULL)
);

CREATE INDEX idx_notification_recipients_enabled_name
    ON ra_fcb.notification_recipients (enabled, name);
CREATE INDEX idx_notification_recipients_user
    ON ra_fcb.notification_recipients (user_id)
    WHERE user_id IS NOT NULL;

CREATE TABLE ra_fcb.notification_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_notification_groups_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_groups_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL
);

CREATE TABLE ra_fcb.notification_group_members (
    group_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notification_group_members PRIMARY KEY (group_id, recipient_id),
    CONSTRAINT fk_notification_group_members_group
        FOREIGN KEY (group_id) REFERENCES ra_fcb.notification_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_group_members_recipient
        FOREIGN KEY (recipient_id) REFERENCES ra_fcb.notification_recipients(id) ON DELETE RESTRICT
);

CREATE INDEX idx_notification_group_members_recipient
    ON ra_fcb.notification_group_members (recipient_id);

CREATE TABLE ra_fcb.notification_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_notification_policies_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_policies_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL
);

CREATE TABLE ra_fcb.notification_routes (
    id BIGSERIAL PRIMARY KEY,
    notification_policy_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    recipient_id BIGINT,
    group_id BIGINT,
    severity VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_interval_seconds INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_routes_policy
        FOREIGN KEY (notification_policy_id) REFERENCES ra_fcb.notification_policies(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_routes_channel
        FOREIGN KEY (channel_id) REFERENCES ra_fcb.notification_channels(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_routes_recipient
        FOREIGN KEY (recipient_id) REFERENCES ra_fcb.notification_recipients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_routes_group
        FOREIGN KEY (group_id) REFERENCES ra_fcb.notification_groups(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notification_routes_target
        CHECK ((recipient_id IS NOT NULL AND group_id IS NULL)
            OR (recipient_id IS NULL AND group_id IS NOT NULL)),
    CONSTRAINT chk_notification_routes_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_notification_routes_event
        CHECK (event_type IN ('CREATED', 'RECURRENCE', 'ESCALATED', 'RECOVERED', 'REMINDER')),
    CONSTRAINT chk_notification_routes_interval
        CHECK (minimum_interval_seconds >= 0)
);

CREATE INDEX idx_notification_routes_policy_match
    ON ra_fcb.notification_routes (notification_policy_id, enabled, severity, event_type);
CREATE INDEX idx_notification_routes_channel
    ON ra_fcb.notification_routes (channel_id, enabled);

ALTER TABLE ra_fcb.alert_rules
    ADD CONSTRAINT fk_alert_rules_notification_policy
    FOREIGN KEY (notification_policy_id)
    REFERENCES ra_fcb.notification_policies(id)
    ON DELETE RESTRICT;

CREATE TABLE ra_fcb.notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    alert_history_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    destination_snapshot VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(500) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    provider_message_id VARCHAR(500),
    last_error TEXT,
    message_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_deliveries_alert
        FOREIGN KEY (alert_id) REFERENCES ra_fcb.alerts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_deliveries_history
        FOREIGN KEY (alert_history_id) REFERENCES ra_fcb.alert_history(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_deliveries_channel
        FOREIGN KEY (channel_id) REFERENCES ra_fcb.notification_channels(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_deliveries_recipient
        FOREIGN KEY (recipient_id) REFERENCES ra_fcb.notification_recipients(id) ON DELETE RESTRICT,
    CONSTRAINT chk_notification_deliveries_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_notification_deliveries_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_notification_deliveries_payload_object
        CHECK (jsonb_typeof(message_payload) = 'object')
);

CREATE INDEX idx_notification_deliveries_due
    ON ra_fcb.notification_deliveries (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_notification_deliveries_alert
    ON ra_fcb.notification_deliveries (alert_id, created_at DESC);
CREATE INDEX idx_notification_deliveries_channel_status
    ON ra_fcb.notification_deliveries (channel_id, status, created_at DESC);
