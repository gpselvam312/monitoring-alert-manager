-- =========================================================
-- Map permissions to roles
-- =========================================================

-- ADMIN: full access
INSERT INTO ra_fcb.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ra_fcb.roles r
CROSS JOIN ra_fcb.permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;


-- OPERATOR: monitoring and operational access
INSERT INTO ra_fcb.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ra_fcb.roles r
JOIN ra_fcb.permissions p
    ON p.name IN (
        'MONITORING_VIEW',
        'MONITORING_RUN',
        'ALERT_VIEW',
        'SCHEDULE_VIEW',
        'MACHINE_VIEW',
        'NOTIFICATION_VIEW'
    )
WHERE r.name = 'OPERATOR'
ON CONFLICT DO NOTHING;


-- VIEWER: read-only monitoring access
INSERT INTO ra_fcb.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ra_fcb.roles r
JOIN ra_fcb.permissions p
    ON p.name IN (
        'MONITORING_VIEW',
        'ALERT_VIEW',
        'SCHEDULE_VIEW',
        'MACHINE_VIEW'
    )
WHERE r.name = 'VIEWER'
ON CONFLICT DO NOTHING;