-- Convert legacy global environments into application-owned records while
-- preserving references from monitoring jobs, dashboard tabs and machines.
ALTER TABLE ra_fcb.environments
    ADD COLUMN IF NOT EXISTS application_id BIGINT;

CREATE TEMP TABLE legacy_environment_snapshot ON COMMIT DROP AS
SELECT id, name, description, enabled, created_at, updated_at, created_by, updated_by, application_id
FROM ra_fcb.environments;

ALTER TABLE ra_fcb.environments
    DROP CONSTRAINT IF EXISTS environments_name_key;

-- Assign each legacy global row to the first application that does not already
-- own an environment with that name.
UPDATE ra_fcb.environments e
SET application_id = (
    SELECT app.id
    FROM ra_fcb.applications app
    WHERE NOT EXISTS (
        SELECT 1
        FROM ra_fcb.environments existing
        WHERE existing.application_id = app.id
          AND LOWER(existing.name) = LOWER(e.name)
          AND existing.id <> e.id
    )
    ORDER BY app.id
    LIMIT 1
)
WHERE e.application_id IS NULL
  AND EXISTS (SELECT 1 FROM ra_fcb.applications);

-- Clone only environments that were global before this migration, and only
-- where the target application does not already have an equivalent record.
INSERT INTO ra_fcb.environments
    (name, description, enabled, created_at, updated_at, created_by, updated_by, application_id)
SELECT legacy.name, legacy.description, legacy.enabled, legacy.created_at, legacy.updated_at,
       legacy.created_by, legacy.updated_by, app.id
FROM legacy_environment_snapshot legacy
CROSS JOIN ra_fcb.applications app
WHERE legacy.application_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ra_fcb.environments existing
      WHERE existing.application_id = app.id
        AND LOWER(existing.name) = LOWER(legacy.name)
  );

-- Repair legacy cross-application references if environments were already
-- partially scoped before this migration.
INSERT INTO ra_fcb.environments
    (name, description, enabled, created_at, updated_at, created_by, updated_by, application_id)
SELECT DISTINCT legacy.name, legacy.description, legacy.enabled, legacy.created_at, legacy.updated_at,
       legacy.created_by, legacy.updated_by, refs.application_id
FROM legacy_environment_snapshot legacy
JOIN (
    SELECT environment_id, application_id
    FROM ra_fcb.monitoring_jobs
    WHERE environment_id IS NOT NULL AND application_id IS NOT NULL
    UNION
    SELECT environment_id, application_id
    FROM ra_fcb.dashboard_tabs
    WHERE environment_id IS NOT NULL AND application_id IS NOT NULL
) refs ON refs.environment_id = legacy.id
WHERE legacy.application_id IS NOT NULL
  AND refs.application_id <> legacy.application_id
  AND NOT EXISTS (
      SELECT 1
      FROM ra_fcb.environments existing
      WHERE existing.application_id = refs.application_id
        AND LOWER(existing.name) = LOWER(legacy.name)
  );

-- Repoint jobs to an environment owned by their own application.
UPDATE ra_fcb.monitoring_jobs job
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON LOWER(target.name) = LOWER(legacy.name)
WHERE job.environment_id = legacy.id
  AND job.application_id = target.application_id;

-- Backfill dashboard application IDs only when the configured widgets identify
-- one application and one environment.
WITH widget_sources AS (
    SELECT w.tab_id,
           COALESCE(j.application_id, legacy_job.application_id) AS application_id,
           COALESCE(j.environment_id, legacy_job.environment_id) AS environment_id
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
       AND COUNT(DISTINCT environment_id) = 1
)
UPDATE ra_fcb.dashboard_tabs tab
SET application_id = candidates.application_id
FROM tab_app_candidates candidates
WHERE tab.id = candidates.tab_id
  AND tab.application_id IS NULL;

-- Repair tab references where the tab's application differs from the old
-- environment owner, creating a matching application-owned copy if necessary.
INSERT INTO ra_fcb.environments
    (name, description, enabled, created_at, updated_at, created_by, updated_by, application_id)
SELECT DISTINCT legacy.name, legacy.description, legacy.enabled, legacy.created_at, legacy.updated_at,
       legacy.created_by, legacy.updated_by, tab.application_id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.dashboard_tabs tab ON tab.environment_id = legacy.id
WHERE legacy.application_id IS NOT NULL
  AND tab.application_id IS NOT NULL
  AND tab.application_id <> legacy.application_id
  AND NOT EXISTS (
      SELECT 1
      FROM ra_fcb.environments existing
      WHERE existing.application_id = tab.application_id
        AND LOWER(existing.name) = LOWER(legacy.name)
  );

UPDATE ra_fcb.dashboard_tabs tab
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON LOWER(target.name) = LOWER(legacy.name)
WHERE tab.environment_id = legacy.id
  AND tab.application_id = target.application_id;

-- Legacy jobs/tabs without an application cannot be mapped precisely. Point
-- those references (and legacy machine references) to the first application's
-- same-named environment before removing any now-redundant global row.
UPDATE ra_fcb.monitoring_jobs job
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON LOWER(target.name) = LOWER(legacy.name)
WHERE job.environment_id = legacy.id
  AND job.application_id IS NULL
  AND target.application_id = (SELECT MIN(id) FROM ra_fcb.applications);

UPDATE ra_fcb.dashboard_tabs tab
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON LOWER(target.name) = LOWER(legacy.name)
WHERE tab.environment_id = legacy.id
  AND tab.application_id IS NULL
  AND target.application_id = (SELECT MIN(id) FROM ra_fcb.applications);

UPDATE ra_fcb.machines machine
SET environment_id = target.id
FROM legacy_environment_snapshot legacy
JOIN ra_fcb.environments target ON LOWER(target.name) = LOWER(legacy.name)
WHERE machine.environment_id = legacy.id
  AND legacy.application_id IS NULL
  AND target.application_id = (SELECT MIN(id) FROM ra_fcb.applications);

DELETE FROM ra_fcb.environments e
WHERE e.application_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.monitoring_jobs j WHERE j.environment_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.dashboard_tabs t WHERE t.environment_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM ra_fcb.machines m WHERE m.environment_id = e.id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_environments_application'
          AND conrelid = 'ra_fcb.environments'::regclass
    ) THEN
        ALTER TABLE ra_fcb.environments
            ADD CONSTRAINT fk_environments_application
            FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id);
    END IF;
END $$;

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
