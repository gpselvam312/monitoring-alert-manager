-- Register External Applications in the existing database-driven Administration menu.
-- Sidebar rendering remains data-driven; do not add menu links directly to sidebar.html.
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission, enabled)
SELECT 'EXTERNAL_APPLICATIONS', 'External Applications', '/administration/external-applications',
       'bi-window-stack', p.id, 50, 'MONITORING_CONFIG', TRUE
FROM ra_fcb.menu_items p
WHERE p.menu_key = 'ADMINISTRATION'
  AND NOT EXISTS (
      SELECT 1 FROM ra_fcb.menu_items WHERE menu_key = 'EXTERNAL_APPLICATIONS'
  );
