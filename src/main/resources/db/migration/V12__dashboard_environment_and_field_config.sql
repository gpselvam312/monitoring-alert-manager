-- Associate each dashboard tab with one environment and keep widget presentation
-- configuration separate from the normalized monitoring result.
ALTER TABLE ra_fcb.dashboard_tabs
    ADD COLUMN environment_id BIGINT;

ALTER TABLE ra_fcb.dashboard_tabs
    ADD CONSTRAINT fk_dashboard_tabs_environment
    FOREIGN KEY (environment_id)
    REFERENCES ra_fcb.environments(id);

ALTER TABLE ra_fcb.dashboard_widgets
    ADD COLUMN field_config JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Preserve existing dashboards where every configured widget points to the same
-- environment. Leave mixed-environment tabs unassigned for explicit admin review.
WITH tab_environment_candidates AS (
    SELECT
        w.tab_id,
        MIN(COALESCE(job.environment_id, legacy_job.environment_id)) AS environment_id
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
    WHERE COALESCE(job.environment_id, legacy_job.environment_id) IS NOT NULL
    GROUP BY w.tab_id
    HAVING COUNT(DISTINCT COALESCE(job.environment_id, legacy_job.environment_id)) = 1
),
environment_candidates_used_once AS (
    SELECT environment_id
    FROM tab_environment_candidates
    GROUP BY environment_id
    HAVING COUNT(*) = 1
)
UPDATE ra_fcb.dashboard_tabs tab
SET environment_id = candidate.environment_id
FROM tab_environment_candidates candidate
JOIN environment_candidates_used_once unique_candidate
  ON unique_candidate.environment_id = candidate.environment_id
WHERE tab.id = candidate.tab_id;

-- A dashboard tab represents one environment. NULL values remain allowed for
-- legacy tabs that need an administrator to resolve mixed environment mappings.
CREATE UNIQUE INDEX uk_dashboard_tabs_environment_id
    ON ra_fcb.dashboard_tabs(environment_id)
    WHERE environment_id IS NOT NULL;
