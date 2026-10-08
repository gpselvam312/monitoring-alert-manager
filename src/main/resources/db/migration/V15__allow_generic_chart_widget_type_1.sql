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
        'CHART'
    )
);