-- Schedules now consistently use the JVM/server timezone.
-- Existing schedule times are retained and interpreted in the server timezone.
ALTER TABLE ra_fcb.schedules DROP COLUMN IF EXISTS timezone;
