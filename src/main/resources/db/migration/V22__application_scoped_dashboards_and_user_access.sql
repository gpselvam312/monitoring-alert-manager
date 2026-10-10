ALTER TABLE ra_fcb.dashboard_tabs ADD COLUMN application_id BIGINT;
ALTER TABLE ra_fcb.dashboard_tabs ADD CONSTRAINT fk_dashboard_tabs_application
    FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id);

-- Backfill tabs only when all configured monitoring sources identify one application and environment.
WITH widget_sources AS (
    SELECT w.tab_id, COALESCE(j.application_id, legacy_job.application_id) AS application_id,
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
),
tab_app_candidates AS (
    SELECT tab_id, MIN(application_id) AS application_id
    FROM widget_sources
    WHERE application_id IS NOT NULL
    GROUP BY tab_id
    HAVING COUNT(DISTINCT application_id) = 1 AND COUNT(DISTINCT environment_id) = 1
)
UPDATE ra_fcb.dashboard_tabs t SET application_id = c.application_id
FROM tab_app_candidates c WHERE c.tab_id = t.id;

ALTER TABLE ra_fcb.dashboard_tabs DROP CONSTRAINT IF EXISTS uk_dashboard_tabs_name;
CREATE UNIQUE INDEX IF NOT EXISTS uk_dashboard_tabs_application_environment_name
    ON ra_fcb.dashboard_tabs(application_id, environment_id, LOWER(name))
    WHERE application_id IS NOT NULL AND environment_id IS NOT NULL;

CREATE TABLE ra_fcb.user_applications (
    user_id BIGINT NOT NULL REFERENCES ra_fcb.users(id) ON DELETE CASCADE,
    application_id BIGINT NOT NULL REFERENCES ra_fcb.applications(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, application_id)
);

-- Existing accounts keep their current visibility until an administrator configures narrower grants.
INSERT INTO ra_fcb.user_applications(user_id, application_id)
SELECT u.id, a.id FROM ra_fcb.users u CROSS JOIN ra_fcb.applications a
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_user_applications_application ON ra_fcb.user_applications(application_id);
