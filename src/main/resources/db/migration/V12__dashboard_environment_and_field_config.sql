-- Associate each dashboard tab with one environment and keep widget presentation
-- configuration separate from the normalized monitoring result.
ALTER TABLE ra_fcb.dashboard_tabs
    ADD COLUMN environment_id BIGINT;

ALTER TABLE ra_fcb.dashboard_tabs
    ADD CONSTRAINT fk_dashboard_tabs_environment
    FOREIGN KEY (environment_id)
    REFERENCES ra_fcb.environments(id);

-- A dashboard tab represents one environment. PostgreSQL allows multiple NULL
-- values here so existing tabs remain migratable until explicitly assigned.
CREATE UNIQUE INDEX uk_dashboard_tabs_environment_id
    ON ra_fcb.dashboard_tabs(environment_id)
    WHERE environment_id IS NOT NULL;

ALTER TABLE ra_fcb.dashboard_widgets
    ADD COLUMN field_config JSONB NOT NULL DEFAULT '{}'::jsonb;
