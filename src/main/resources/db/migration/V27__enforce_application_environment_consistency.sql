-- Enforce that jobs and dashboard tabs cannot reference an environment owned
-- by a different application. Composite unique index is required as the FK target.
CREATE UNIQUE INDEX IF NOT EXISTS uq_environments_id_application
    ON ra_fcb.environments(id, application_id);

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT fk_monitoring_jobs_application_environment
    FOREIGN KEY (environment_id, application_id)
    REFERENCES ra_fcb.environments(id, application_id)
    NOT VALID;

ALTER TABLE ra_fcb.monitoring_jobs
    VALIDATE CONSTRAINT fk_monitoring_jobs_application_environment;

ALTER TABLE ra_fcb.dashboard_tabs
    ADD CONSTRAINT fk_dashboard_tabs_application_environment
    FOREIGN KEY (environment_id, application_id)
    REFERENCES ra_fcb.environments(id, application_id)
    NOT VALID;

ALTER TABLE ra_fcb.dashboard_tabs
    VALIDATE CONSTRAINT fk_dashboard_tabs_application_environment;
