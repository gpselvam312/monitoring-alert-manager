-- Add stable display ordering for the generic Global Settings screen.
ALTER TABLE ra_fcb.system_settings
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 100;

UPDATE ra_fcb.system_settings
SET sort_order = CASE setting_key
    WHEN 'APPLICATION_NAME' THEN 10
    WHEN 'THEME' THEN 20
    WHEN 'SITE_OFFLINE' THEN 30
    WHEN 'MAINTENANCE_MESSAGE' THEN 40
    WHEN 'MAINTENANCE_BYPASS_IPS' THEN 50
    WHEN 'SCRIPT_ALLOWED_DIRECTORIES' THEN 60
    ELSE 100
END;

-- Empty array preserves the existing environment/application property as the script-root fallback.
INSERT INTO ra_fcb.system_settings
    (setting_key, setting_value, data_type, description, sort_order)
VALUES
    ('SCRIPT_ALLOWED_DIRECTORIES', '[]', 'JSON',
     'Trusted directories for SCRIPT monitoring jobs, as a JSON array of absolute directory paths. An empty array uses MONITORING_SCRIPT_ALLOWED_DIRECTORIES or the application default.',
     60)
ON CONFLICT (setting_key) DO NOTHING;
