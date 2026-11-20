ALTER TABLE ra_fcb.menu_items
    ADD COLUMN IF NOT EXISTS section_name VARCHAR(80);

UPDATE ra_fcb.menu_items
SET required_permission = 'ROLE_ADMIN'
WHERE menu_key IN ('USERS', 'ROLES', 'MENU_MANAGEMENT');

UPDATE ra_fcb.menu_items
SET enabled = FALSE
WHERE menu_key = 'AUDIT_LOG';

UPDATE ra_fcb.menu_items
SET section_name = CASE
    WHEN menu_key = 'DASHBOARD' THEN 'Main'
    WHEN menu_key IN ('MONITORING_JOBS', 'STREAMING_JOBS', 'MACHINES') THEN 'Monitoring'
    WHEN menu_key = 'SCHEDULES' THEN 'Scheduling'
    WHEN parent_id IS NULL AND (url IS NULL OR BTRIM(url) = '') THEN menu_name
    ELSE 'Main'
END
WHERE section_name IS NULL OR BTRIM(section_name) = '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_menu_items_menu_key_lower
    ON ra_fcb.menu_items (LOWER(menu_key));
