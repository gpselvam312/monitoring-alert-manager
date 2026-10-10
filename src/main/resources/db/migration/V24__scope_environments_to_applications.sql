-- Convert the former global environment master to application-owned environments.
-- Existing environment IDs are retained for the first application; equivalent rows are
-- created for the remaining applications and all job/tab references are remapped.

ALTER TABLE ra_fcb.environments
    ADD COLUMN IF NOT EXISTS application_id BIGINT;

CREATE TEMP TABLE legacy_environment_snapshot ON COMMIT DROP AS
SELECT id, name, description, enabled, created_at, updated_at, created_by, updated_by
FROM ra_fcb.environments;

ALTER TABLE ra_fcb.environments
    DROP CONSTRAINT IF EXISTS environments_name_key;

-- Keep each legacy row for the first application, then create one row per remaining app.
UPDATE ra_fcb.environments e
SET application_id = (SELECT MIN(id) FROM ra_fcb.applications)
WHERE e.application_id IS NULL
  AND EXISTS (SELECT 1 FROM ra_fcb.applications);

INSERT INTO ra_fcb.environments
    (name, description, enabled, created_at, updated_at, created_by, updated_by, application_id)
SELECT legacy.name, legacy.description, legacy.enabled, legacy.created_at, legacy.updated_at,
       legacy.created_by, legacy.updated_by, app.id
FROM legacy_environment_snapshot legacy
CROSS JOIN ra_fcb.applications app
WHERE app.id <> (SELECT MIN(id) FROM ra_fcb.applications);

-- Repoint each monitoring job to the copy owned by its own application.
UPDATE ra_fcb.monitoring_jobs job
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON target.name = legacy.name
WHERE job.environment_id = legacy.id
  AND job.application_id = target.application_id;

-- Recover application IDs for tabs that can be unambiguously inferred from their widgets.
WITH widget_sources AS (
    SELECT w.tab_id,
           COALESCE(j.application_id, legacy_job.application_id) AS application_id
    FROM ra_fcb.dashboard_widgets w
    LEFT JOIN ra_fcb.monitoring_jobs j
      ON w.data_source_type = 'MONITORING_JOB' AND j.id = w.data_source_id
    LEFT JOIN ra_fcb.monitoring_results legacy_result
      ON w.data_source_type = 'MONITORING_RESULT' AND legacy_result.id = w.data_source_id
    LEFT JOIN ra_fcb.monitoring_executions legacy_execution
      ON legacy_execution.id = legacy_result.execution_id
    LEFT JOIN ra_fcb.monitoring_jobs legacy_job
      ON legacy_job.id = legacy_execution.monitoring_job_id
), tab_app_candidates AS (
    SELECT tab_id, MIN(application_id) AS application_id
    FROM widget_sources
    WHERE application_id IS NOT NULL
    GROUP BY tab_id
    HAVING COUNT(DISTINCT application_id) = 1
)
UPDATE ra_fcb.dashboard_tabs tab
SET application_id = candidates.application_id
FROM tab_app_candidates candidates
WHERE tab.id = candidates.tab_id
  AND tab.application_id IS NULL;

-- Repoint dashboard tabs to the environment belonging to the tab's application.
UPDATE ra_fcb.dashboard_tabs tab
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON target.name = legacy.name
WHERE tab.environment_id = legacy.id
  AND tab.application_id = target.application_id;

ALTER TABLE ra_fcb.environments
    ADD CONSTRAINT fk_environments_application
    FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_environments_application_name
    ON ra_fcb.environments(application_id, LOWER(name));

CREATE INDEX IF NOT EXISTS idx_environments_application_enabled
    ON ra_fcb.environments(application_id, enabled, name);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ra_fcb.applications) THEN
        ALTER TABLE ra_fcb.environments ALTER COLUMN application_id SET NOT NULL;
    END IF;
END $$;
