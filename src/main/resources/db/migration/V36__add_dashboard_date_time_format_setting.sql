-- Make the dashboard DateTime display format configurable from Global Settings.
-- The value uses Java DateTimeFormatter pattern syntax, matching the application's existing default.
INSERT INTO ra_fcb.system_settings
    (setting_key, setting_value, data_type, description, sort_order)
VALUES
    ('DATE_TIME_FORMAT', 'MM/dd/yyyy hh:mm:ss a', 'STRING',
     'Date/time display pattern used by dashboard Summary Table columns and cards. Uses Java date/time pattern syntax.',
     70)
ON CONFLICT (setting_key) DO NOTHING;
