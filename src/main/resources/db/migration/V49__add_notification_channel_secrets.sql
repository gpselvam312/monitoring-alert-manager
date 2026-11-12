-- Store SMTP passwords, Teams webhook URLs and WhatsApp API tokens encrypted
-- with the existing external-secret encryption service. Plain secrets never
-- belong in notification channel JSON configuration.
CREATE TABLE ra_fcb.notification_channel_secrets (
    channel_id BIGINT NOT NULL,
    secret_key VARCHAR(100) NOT NULL,
    encrypted_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT pk_notification_channel_secrets PRIMARY KEY (channel_id, secret_key),
    CONSTRAINT fk_notification_channel_secrets_channel
        FOREIGN KEY (channel_id) REFERENCES ra_fcb.notification_channels(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_channel_secrets_user
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id) ON DELETE SET NULL
);

ALTER TABLE ra_fcb.notification_channels
    ADD CONSTRAINT fk_notification_channels_credential_reference
    FOREIGN KEY (id, credential_reference)
    REFERENCES ra_fcb.notification_channel_secrets(channel_id, secret_key)
    DEFERRABLE INITIALLY DEFERRED;

-- Teams webhook deliveries are channel-level rather than per-person. A nullable
-- recipient reference allows one delivery per Teams route without duplicating
-- the same channel post for every member of a notification group.
ALTER TABLE ra_fcb.notification_deliveries
    ALTER COLUMN recipient_id DROP NOT NULL;
