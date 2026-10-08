-- =========================================================
-- Seed application permissions
-- =========================================================

INSERT INTO ra_fcb.permissions (name, description)
VALUES
    ('MONITORING_VIEW', 'View monitoring jobs and monitoring status'),
    ('MONITORING_RUN', 'Run monitoring jobs manually'),
    ('MONITORING_CONFIG', 'Create and modify monitoring jobs'),

    ('ALERT_VIEW', 'View alerts and alert history'),
    ('ALERT_CONFIG', 'Configure alert rules'),

    ('SCHEDULE_VIEW', 'View schedules'),
    ('SCHEDULE_CONFIG', 'Create and modify schedules'),

    ('MACHINE_VIEW', 'View machines'),
    ('MACHINE_CONFIG', 'Create and modify machines'),

    ('NOTIFICATION_VIEW', 'View notification configuration and history'),
    ('NOTIFICATION_CONFIG', 'Configure notification channels and recipients'),

    ('USER_CONFIG', 'Create and modify users and roles'),
    ('SYSTEM_CONFIG', 'Configure system settings'),

    ('SERVICENOW_CONFIG', 'Configure ServiceNow integration'),

    ('DATA_EXPUNGE', 'Preview and permanently delete historical monitoring data'),

    ('AUDIT_VIEW', 'View audit history')
ON CONFLICT (name) DO NOTHING;