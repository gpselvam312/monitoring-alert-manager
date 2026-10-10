-- Application-scoped dashboard tabs and user application access.
-- Environment records remain reusable across applications; each dashboard tab
-- explicitly binds an application and environment.

ALTER TABLE ra_fcb.dashboard_tabs
    ADD COLUMN application_id BIGINT;

ALTER TABLE ra_fcb.dashboard_tabs
    ADD CONSTRAINT fk_dashboard_tabs_application
    FOREIGN KEY (application_id)
    REFERENCES ra_fcb.applications(id);

CREATE INDEX idx_dashboard_tabs_application_environment
    ON ra_fcb.dashboard_tabs(application_id, environment_id, enabled, sort_order);

-- Backfill tabs where all monitoring sources resolve to exactly one application.
WITH widget_applications AS (
    SELECT
        w.tab_id,
        COALESCE(job.application_id, legacy_job.application_id) AS application_id
    FROM ra_fcb.dashboard_widgets w
    LEFT JOIN ra_fcb.monitoring_jobs job
        ON w.data_source_type = 'MONITORING_JOB'
       AND job.id = w.data_source_id
    LEFT JOIN ra_fcb.monitoring_results legacy_result
        ON w.data_source_type = 'MONITORING_RESULT'
       AND legacy_result.id = w.data_source_id
    LEFT JOIN ra_fcb.monitoring_executions legacy_execution
        ON legacy_execution.id = legacy_result.execution_id
    LEFT JOIN ra_fcb.monitoring_jobs legacy_job
        ON legacy_job.id = legacy_execution.monitoring_job_id
),
single_application_tabs AS (
    SELECT tab_id, MIN(application_id) AS application_id
    FROM widget_applications
    WHERE application_id IS NOT NULL
    GROUP BY tab_id
    HAVING COUNT(DISTINCT application_id) = 1
)
UPDATE ra_fcb.dashboard_tabs tab
SET application_id = candidate.application_id
FROM single_application_tabs candidate
WHERE tab.id = candidate.tab_id
  AND tab.application_id IS NULL;

-- For a single-application installation, unambiguous legacy tabs can safely
-- inherit the only application even if their widgets have no data source.
UPDATE ra_fcb.dashboard_tabs
SET application_id = (SELECT MIN(id) FROM ra_fcb.applications)
WHERE application_id IS NULL
  AND (SELECT COUNT(*) FROM ra_fcb.applications) = 1;

CREATE TABLE ra_fcb.user_applications (
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, application_id),
    CONSTRAINT fk_user_applications_user
        FOREIGN KEY (user_id) REFERENCES ra_fcb.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_applications_application
        FOREIGN KEY (application_id) REFERENCES ra_fcb.applications(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_applications_application
    ON ra_fcb.user_applications(application_id);

-- Preserve existing access during rollout. New users are explicitly assigned
-- applications through User Administration. ADMIN users also have global access.
INSERT INTO ra_fcb.user_applications (user_id, application_id)
SELECT u.id, a.id
FROM ra_fcb.users u
CROSS JOIN ra_fcb.applications a
ON CONFLICT DO NOTHING;
