ALTER TABLE ra_fcb.monitoring_jobs
    ADD COLUMN result_parser_config JSONB NOT NULL DEFAULT '{}'::jsonb;
