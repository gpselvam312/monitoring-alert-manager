-- Allow the reusable Speedometer / Gauge dashboard widget type.
-- Preserve all widget types currently supported by the database constraint.
ALTER TABLE ra_fcb.dashboard_widgets
    DROP CONSTRAINT ck_dashboard_widgets_type;

ALTER TABLE ra_fcb.dashboard_widgets
    ADD CONSTRAINT ck_dashboard_widgets_type
    CHECK (
        widget_type IN (
            'STAT',
            'STATUS',
            'TABLE',
            'TEXT',
            'CHART',
            'SYSTEM_METRICS',
            'ELASTICSEARCH',
            'SUMMARY_TABLE',
            'IFRAME',
            'SPEEDOMETER'
        )
    );
