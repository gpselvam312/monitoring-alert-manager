ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN dashboard_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dashboard_title VARCHAR(200),
    ADD COLUMN dashboard_width INTEGER NOT NULL DEFAULT 6,
    ADD COLUMN dashboard_sort_order INTEGER NOT NULL DEFAULT 100;