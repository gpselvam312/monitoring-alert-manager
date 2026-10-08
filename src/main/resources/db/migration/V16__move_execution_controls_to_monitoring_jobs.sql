-- Monitoring job execution controls
ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN store_result BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN manual_run_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN allow_concurrent_execution BOOLEAN NOT NULL DEFAULT FALSE;

-- Dashboard presentation is owned by dashboard_widgets.
ALTER TABLE ra_fcb.monitoring_jobs
    DROP COLUMN dashboard_enabled,
    DROP COLUMN dashboard_title,
    DROP COLUMN dashboard_width,
    DROP COLUMN dashboard_sort_order;

-- Result retention is owned by the monitoring job, not the dashboard widget.
ALTER TABLE ra_fcb.dashboard_widgets
    DROP COLUMN store_result;

-- DashboardWidgetResult duplicated monitoring history. Historical monitoring data
-- is stored in monitoring_executions / monitoring_results.
DROP TABLE IF EXISTS ra_fcb.dashboard_widget_results;
