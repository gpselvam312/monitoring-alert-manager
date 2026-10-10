-- Add a dedicated card renderer for multi-server health summaries.
ALTER TABLE ra_fcb.dashboard_widgets
    DROP CONSTRAINT IF EXISTS ck_dashboard_widgets_type;

ALTER TABLE ra_fcb.dashboard_widgets
    ADD CONSTRAINT ck_dashboard_widgets_type
    CHECK (
        widget_type IN (
            'STAT',
            'STATUS',
            'TABLE',
            'CHART',
            'SERVER_HEALTH',
            'LINE_CHART',
            'BAR_CHART',
            'PIE_CHART',
            'DONUT_CHART',
            'TEXT'
        )
    );
