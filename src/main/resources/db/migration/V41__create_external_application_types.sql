-- Product types are registry data, not application-code constants.
CREATE TABLE ra_fcb.external_application_types (
    code VARCHAR(30) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ra_fcb.external_application_types (code, display_name, enabled, sort_order)
VALUES
    ('GRAFANA', 'Grafana', TRUE, 10),
    ('CONSUL', 'Consul', TRUE, 20),
    ('OTHER', 'Other', TRUE, 100);

ALTER TABLE ra_fcb.external_applications
    DROP CONSTRAINT IF EXISTS chk_external_applications_type;

ALTER TABLE ra_fcb.external_applications
    ADD CONSTRAINT fk_external_applications_type
        FOREIGN KEY (external_type)
        REFERENCES ra_fcb.external_application_types(code);
