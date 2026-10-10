-- Populate application and environment IDs for tabs whose widgets all use one application/environment.
WITH sources AS (
 SELECT w.tab_id, COALESCE(j.application_id, lj.application_id) app_id,
        COALESCE(j.environment_id, lj.environment_id) env_id
 FROM ra_fcb.dashboard_widgets w
 LEFT JOIN ra_fcb.monitoring_jobs j ON w.data_source_type = 'MONITORING_JOB' AND j.id = w.data_source_id
 LEFT JOIN ra_fcb.monitoring_results r ON w.data_source_type = 'MONITORING_RESULT' AND r.id = w.data_source_id
 LEFT JOIN ra_fcb.monitoring_executions x ON x.id = r.execution_id
 LEFT JOIN ra_fcb.monitoring_jobs lj ON lj.id = x.monitoring_job_id
), candidates AS (
 SELECT tab_id, MIN(app_id) app_id, MIN(env_id) env_id
 FROM sources WHERE app_id IS NOT NULL AND env_id IS NOT NULL
 GROUP BY tab_id HAVING COUNT(DISTINCT app_id) = 1 AND COUNT(DISTINCT env_id) = 1
)
UPDATE ra_fcb.dashboard_tabs t SET application_id = c.app_id, environment_id = c.env_id
FROM candidates c WHERE c.tab_id = t.id AND (t.application_id IS NULL OR t.environment_id IS NULL);
