ALTER TABLE ra_fcb.notification_deliveries
    ADD COLUMN locked_at TIMESTAMPTZ;

CREATE INDEX idx_notification_deliveries_stale
    ON ra_fcb.notification_deliveries (locked_at)
    WHERE status = 'SENDING';
