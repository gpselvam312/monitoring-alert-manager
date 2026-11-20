-- Notification channels, recipients, groups and routing are global configuration records.
-- Only a platform administrator can view or change these shared destinations and credentials.
UPDATE ra_fcb.menu_items
SET required_permission = 'ROLE_ADMIN'
WHERE url LIKE '/administration/notifications%';
