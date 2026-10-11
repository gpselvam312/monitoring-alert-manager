-- The menu table predates this migration in some environments and uses
-- menu_name/url/parent_id/required_permission. Keep that schema as the canonical
-- model; do not replace or recreate an existing table.
DO $$
BEGIN
    IF to_regclass('ra_fcb.menu_items') IS NULL THEN
        CREATE TABLE ra_fcb.menu_items (
            id BIGSERIAL PRIMARY KEY,
            menu_key VARCHAR(80) NOT NULL UNIQUE,
            menu_name VARCHAR(100) NOT NULL,
            url VARCHAR(500),
            icon VARCHAR(80),
            parent_id BIGINT,
            display_order INTEGER NOT NULL,
            required_permission VARCHAR(100),
            enabled BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            created_by BIGINT,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by BIGINT,
            CONSTRAINT fk_menu_items_parent
                FOREIGN KEY (parent_id) REFERENCES ra_fcb.menu_items(id) ON DELETE SET NULL
        );
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_menu_items_enabled_order
    ON ra_fcb.menu_items (enabled, display_order);

-- Seed defaults only when absent. Existing menu rows and their IDs/permissions
-- are intentionally left unchanged.
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'DASHBOARD', 'Dashboard', '/dashboard', 'bi-grid-1x2', NULL, 10, 'MONITORING_VIEW', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'DASHBOARD');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'MONITORING_JOBS', 'Monitoring Jobs', '/monitoring/jobs', 'bi-activity', NULL, 20, 'MONITORING_VIEW', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'MONITORING_JOBS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'STREAMING_JOBS', 'Streaming Jobs', '/monitoring/streaming-jobs', 'bi-terminal', NULL, 25, 'MONITORING_VIEW', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'STREAMING_JOBS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'MACHINES', 'Machines', '/monitoring/machines', 'bi-pc-display', NULL, 30, 'MACHINE_VIEW', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'MACHINES');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'SCHEDULES', 'Schedules', '/scheduling/schedules', 'bi-calendar3', NULL, 40, 'SCHEDULE_VIEW', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'SCHEDULES');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ALERTS', 'Alerts', NULL, 'bi-exclamation-triangle', NULL, 50, NULL, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ALERTS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ADMINISTRATION', 'Administration', NULL, 'bi-gear', NULL, 60, 'SYSTEM_CONFIG', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ADMINISTRATION');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ACTIVE_ALERTS', 'Active Alerts', '/alerts', 'bi-exclamation-triangle', p.id, 10, 'ALERT_VIEW', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ALERTS'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ACTIVE_ALERTS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ALERT_HISTORY', 'Alert History', '/alerts/history', 'bi-clock-history', p.id, 20, 'ALERT_VIEW', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ALERTS'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ALERT_HISTORY');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'APPLICATIONS', 'Applications', '/administration/applications', 'bi-box', p.id, 10, 'SYSTEM_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'APPLICATIONS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ENVIRONMENTS', 'Environments', '/administration/environments', 'bi-layers', p.id, 20, 'MONITORING_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ENVIRONMENTS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'USERS', 'Users', '/administration/users', 'bi-person-gear', p.id, 30, 'USER_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'USERS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'SETTINGS', 'Settings', '/administration/settings', 'bi-gear', p.id, 40, 'SYSTEM_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'SETTINGS');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'AUDIT_LOG', 'Audit Log', '/administration/audit', 'bi-journal-text', p.id, 50, 'AUDIT_VIEW', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'AUDIT_LOG');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ROLES', 'Roles', '/administration/roles', 'bi-shield-lock', p.id, 60, 'USER_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ROLES');
