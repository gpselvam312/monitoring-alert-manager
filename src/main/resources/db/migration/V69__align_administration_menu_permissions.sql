-- Keep menu visibility aligned with the permissions enforced by the destination controllers.
UPDATE ra_fcb.menu_items
SET required_permission = 'SYSTEM_CONFIG'
WHERE menu_key IN ('ENVIRONMENTS', 'DASHBOARD_CONFIGURATION');

-- System-wide settings are platform administration, not application-scoped configuration.
UPDATE ra_fcb.menu_items
SET required_permission = 'ROLE_ADMIN'
WHERE menu_key = 'SETTINGS';
