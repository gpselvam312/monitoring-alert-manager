-- Add the Menu Management link without changing existing menu configuration.
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'MENU_MANAGEMENT', 'Menu', '/administration/menus', 'bi-list-nested', p.id, 70, 'SYSTEM_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (
      SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'MENU_MANAGEMENT'
  );
