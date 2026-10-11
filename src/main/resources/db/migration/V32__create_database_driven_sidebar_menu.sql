CREATE TABLE ra_fcb.menu_items (
    id BIGSERIAL PRIMARY KEY,
    menu_key VARCHAR(80) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    route_key VARCHAR(80) NOT NULL,
    icon VARCHAR(80) NOT NULL,
    section_name VARCHAR(100) NOT NULL,
    section_order INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required_role VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_menu_items_enabled_order
    ON ra_fcb.menu_items (enabled, section_order, display_order);

CREATE TABLE ra_fcb.menu_item_permissions (
    menu_item_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_menu_item_permissions PRIMARY KEY (menu_item_id, permission_id),
    CONSTRAINT fk_menu_item_permissions_menu_item
        FOREIGN KEY (menu_item_id) REFERENCES ra_fcb.menu_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_item_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES ra_fcb.permissions(id) ON DELETE CASCADE
);

-- Seed only links that have real application routes. Former href="#" placeholders
-- (alerts, notification configuration/history, and audit log) are intentionally omitted.
INSERT INTO ra_fcb.menu_items
    (menu_key, label, route_key, icon, section_name, section_order, display_order, required_role)
VALUES
    ('dashboard', 'Dashboard', 'dashboard', 'bi-grid-1x2', 'Main', 1, 1, NULL),
    ('monitoring-jobs', 'Monitoring Jobs', 'monitoring-jobs', 'bi-activity', 'Monitoring', 2, 1, NULL),
    ('streaming-jobs', 'Streaming Jobs', 'streaming-jobs', 'bi-terminal', 'Monitoring', 2, 2, NULL),
    ('machines', 'Servers / Machines', 'machines', 'bi-pc-display', 'Monitoring', 2, 3, NULL),
    ('schedules', 'Schedules', 'schedules', 'bi-calendar3', 'Scheduling', 3, 1, NULL),
    ('applications', 'Applications', 'applications', 'bi-box', 'Administration', 4, 1, NULL),
    ('environments', 'Environments', 'environments', 'bi-layers', 'Administration', 4, 2, NULL),
    ('dashboard-configuration', 'Dashboard Configuration', 'dashboard-configuration', 'bi-grid-3x3-gap', 'Administration', 4, 3, NULL),
    ('users', 'Users', 'users', 'bi-person-gear', 'Administration', 4, 4, 'ROLE_ADMIN'),
    ('roles', 'Roles', 'roles', 'bi-shield-lock', 'Administration', 4, 5, 'ROLE_ADMIN'),
    ('settings', 'Settings', 'settings', 'bi-gear', 'Administration', 4, 6, NULL)
ON CONFLICT (menu_key) DO NOTHING;

INSERT INTO ra_fcb.menu_item_permissions (menu_item_id, permission_id)
SELECT mi.id, p.id
FROM (VALUES
    ('monitoring-jobs', 'MONITORING_VIEW'),
    ('streaming-jobs', 'MONITORING_VIEW'),
    ('machines', 'MACHINE_VIEW'),
    ('schedules', 'SCHEDULE_VIEW'),
    ('applications', 'MONITORING_VIEW'),
    ('environments', 'MONITORING_CONFIG'),
    ('dashboard-configuration', 'MONITORING_CONFIG'),
    ('settings', 'SYSTEM_CONFIG')
) AS mapping(menu_key, permission_name)
JOIN ra_fcb.menu_items mi ON mi.menu_key = mapping.menu_key
JOIN ra_fcb.permissions p ON p.name = mapping.permission_name
ON CONFLICT DO NOTHING;
