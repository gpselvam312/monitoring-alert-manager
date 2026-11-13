-- Preserve the display name used when a comment was authored, even if the user is later removed.
ALTER TABLE ra_fcb.alert_comments
    ADD COLUMN author_name VARCHAR(200);
UPDATE ra_fcb.alert_comments c
SET author_name = COALESCE(NULLIF(BTRIM(u.full_name), ''), u.username, 'Former user')
FROM ra_fcb.users u
WHERE c.author_user_id = u.id;
UPDATE ra_fcb.alert_comments
SET author_name = 'Former user'
WHERE author_name IS NULL;
ALTER TABLE ra_fcb.alert_comments
    ALTER COLUMN author_name SET NOT NULL;
