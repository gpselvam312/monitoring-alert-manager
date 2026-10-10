-- Assign exactly one role to each user/application pair.
-- Existing application grants and legacy global roles are migrated without removing
-- the legacy tables, so rollback and existing platform-admin authentication remain possible.

CREATE TABLE ra_fcb.user_application_roles (
    user_id BIGINT NOT NULL REFERENCES ra_fcb.users(id) ON DELETE CASCADE,
    application_id BIGINT NOT NULL REFERENCES ra_fcb.applications(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES ra_fcb.roles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_application_roles PRIMARY KEY (user_id, application_id)
);

-- If legacy data has multiple global roles for one user, preserve the strongest
-- role for each granted application during initial backfill.
INSERT INTO ra_fcb.user_application_roles (user_id, application_id, role_id)
SELECT user_id, application_id, role_id
FROM (
    SELECT ua.user_id, ua.application_id, ur.role_id,
           ROW_NUMBER() OVER (
               PARTITION BY ua.user_id, ua.application_id
               ORDER BY CASE r.name
                   WHEN 'ADMIN' THEN 1
                   WHEN 'OPERATOR' THEN 2
                   WHEN 'VIEWER' THEN 3
                   ELSE 4
               END, r.name
           ) AS role_rank
    FROM ra_fcb.user_applications ua
    JOIN ra_fcb.user_roles ur ON ur.user_id = ua.user_id
    JOIN ra_fcb.roles r ON r.id = ur.role_id
) assignments
WHERE role_rank = 1;

CREATE INDEX idx_user_application_roles_application
    ON ra_fcb.user_application_roles(application_id, role_id);
CREATE INDEX idx_user_application_roles_role
    ON ra_fcb.user_application_roles(role_id);
