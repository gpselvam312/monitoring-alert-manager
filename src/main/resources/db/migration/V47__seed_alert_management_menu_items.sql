-- Reuse the existing database-driven menu records and add only the missing
-- configuration and delivery-history links. Existing IDs and user customizations
-- are preserved.

UPDATE ra_fcb.menu_items
SET enabled = TRUE,
    url = '/alerts',
    required_permission = 'ALERT_VIEW'
WHERE menu_key = 'ACTIVE_ALERTS';

UPDATE ra_fcb.menu_items
SET enabled = TRUE,
    url = '/alerts/history',
    required_permission = 'ALERT_VIEW'
WHERE menu_key = 'ALERT_HISTORY';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ALERT_RULES', 'Alert Rules', '/alerts/rules', 'bi-sliders', p.id, 30, 'ALERT_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ALERTS'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ALERT_RULES');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'NOTIFICATION_CONFIGURATION', 'Notification Configuration', '/administration/notifications',
       'bi-bell', p.id, 60, 'NOTIFICATION_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'NOTIFICATION_CONFIGURATION');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'NOTIFICATION_DELIVERIES', 'Notification Deliveries', '/administration/notifications#notification-deliveries',
       'bi-send', p.id, 70, 'NOTIFICATION_VIEW', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'NOTIFICATION_DELIVERIES');

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'ALERT_RETENTION', 'Alert Retention', '/administration/alert-retention',
       'bi-calendar-x', p.id, 80, 'DATA_EXPUNGE', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'ALERT_RETENTION');
