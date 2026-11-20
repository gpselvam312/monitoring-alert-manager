-- Schedules are shared across monitoring jobs and are not application-scoped.
-- Restrict both viewing and editing until schedule ownership is modeled explicitly.
UPDATE ra_fcb.menu_items
SET required_permission = 'ROLE_ADMIN'
WHERE menu_key = 'SCHEDULES';
