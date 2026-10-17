-- External applications are separate from internal users and their RBAC.
-- Authentication is selected per application; secrets must be encrypted by the application
-- with a key stored outside PostgreSQL, or supplied through secret_reference.
CREATE TABLE ra_fcb.external_applications (
    id BIGSERIAL PRIMARY KEY,
    application_code VARCHAR(50) NOT NULL UNIQUE,
    application_name VARCHAR(150) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_external_applications_auth_type
        CHECK (auth_type IN ('NONE', 'OIDC', 'AUTH_PROXY', 'BASIC', 'CUSTOM'))
);

-- One shared external account per configured application. This table does not replace
-- or modify ra_fcb.users. Never return encrypted_secret in a web/API response.
CREATE TABLE ra_fcb.external_application_accounts (
    id BIGSERIAL PRIMARY KEY,
    external_application_id BIGINT NOT NULL UNIQUE
        REFERENCES ra_fcb.external_applications(id) ON DELETE CASCADE,
    account_name VARCHAR(150) NOT NULL DEFAULT 'Shared account',
    username VARCHAR(255),
    encrypted_secret TEXT,
    secret_reference VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_external_account_secret_source
        CHECK (encrypted_secret IS NULL OR secret_reference IS NULL)
);

CREATE INDEX idx_external_applications_enabled
    ON ra_fcb.external_applications(enabled);
