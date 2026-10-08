ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN application_id BIGINT,
    ADD COLUMN environment_id BIGINT;

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT fk_monitoring_jobs_application
    FOREIGN KEY (application_id)
    REFERENCES ra_fcb.applications(id);

ALTER TABLE ra_fcb.monitoring_jobs
    ADD CONSTRAINT fk_monitoring_jobs_environment
    FOREIGN KEY (environment_id)
    REFERENCES ra_fcb.environments(id);