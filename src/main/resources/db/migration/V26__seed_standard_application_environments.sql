-- Seed the standard environments for every application, including installations
-- where the legacy global environment table was empty.
INSERT INTO ra_fcb.environments
    (name, description, enabled, created_at, updated_at, application_id)
SELECT standard_environment.name,
       standard_environment.description,
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       app.id
FROM ra_fcb.applications app
CROSS JOIN (
    VALUES
        ('SIT', 'System Integration Testing'),
        ('UAT', 'User Acceptance Testing'),
        ('PROD', 'Production')
) AS standard_environment(name, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM ra_fcb.environments existing
    WHERE existing.application_id = app.id
      AND LOWER(existing.name) = LOWER(standard_environment.name)
);
