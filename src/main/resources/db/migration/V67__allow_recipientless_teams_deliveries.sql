-- Teams channels carry their destination in the channel configuration, so a person recipient is optional.
ALTER TABLE ra_fcb.notification_deliveries
    ALTER COLUMN recipient_id DROP NOT NULL;
