-- Dashboard tab names should be unique within an application/environment pair,
-- not globally. This allows each application/environment to use standard names
-- such as Overview, Server Health, and Batch Jobs.
ALTER TABLE ra_fcb.dashboard_tabs
    DROP CONSTRAINT IF EXISTS uk_dashboard_tabs_name;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dashboard_tabs_app_env_name
    ON ra_fcb.dashboard_tabs(application_id, environment_id, LOWER(name))
    WHERE application_id IS NOT NULL
      AND environment_id IS NOT NULL;
