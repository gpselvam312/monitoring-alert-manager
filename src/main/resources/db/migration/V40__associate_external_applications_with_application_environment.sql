-- External integrations can have separate instances per internal application/environment.
-- Keep these references nullable for compatibility with any existing registry entries.
ALTER TABLE ra_fcb.external_applications
    ADD COLUMN external_type VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN application_id BIGINT,
    ADD COLUMN environment_id BIGINT;

ALTER TABLE ra_fcb.external_applications
    ADD CONSTRAINT chk_external_applications_type
        CHECK (external_type IN ('GRAFANA', 'CONSUL', 'OTHER')),
    ADD CONSTRAINT fk_external_applications_application
        FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id),
    ADD CONSTRAINT fk_external_applications_environment
        FOREIGN KEY (environment_id) REFERENCES ra_fcb.environments(id);

CREATE INDEX idx_external_applications_application_environment
    ON ra_fcb.external_applications(application_id, environment_id);

CREATE INDEX idx_external_applications_type
    ON ra_fcb.external_applications(external_type);
