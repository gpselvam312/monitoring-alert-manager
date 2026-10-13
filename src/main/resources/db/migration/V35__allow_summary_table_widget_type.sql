-- Allow the dedicated Summary Table dashboard widget type.
-- Preserve all existing widget types while adding SUMMARY_TABLE.
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
            'SUMMARY_TABLE'
        )
    );
