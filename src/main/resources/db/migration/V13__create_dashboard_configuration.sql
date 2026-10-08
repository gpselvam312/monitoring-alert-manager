-- ============================================================
-- Dashboard Configuration
-- ============================================================

CREATE TABLE dashboard_tabs (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dashboard_tabs_name UNIQUE (name)
);

CREATE INDEX idx_dashboard_tabs_sort_order
    ON dashboard_tabs (sort_order);


-- ============================================================
-- Dashboard Widgets
-- ============================================================

CREATE TABLE dashboard_widgets (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(150) NOT NULL,
    description             VARCHAR(500),

    tab_id                  BIGINT NOT NULL,

    widget_type             VARCHAR(30) NOT NULL,
    chart_type              VARCHAR(30),
    icon                    VARCHAR(100),
    size                    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    sort_order              INTEGER NOT NULL DEFAULT 0,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,

    data_source_type        VARCHAR(30),
    data_source_id          BIGINT,

    auto_refresh            BOOLEAN NOT NULL DEFAULT FALSE,
    refresh_interval        INTEGER,
    refresh_interval_unit   VARCHAR(20),

    store_result            BOOLEAN NOT NULL DEFAULT FALSE,
    details_enabled         BOOLEAN NOT NULL DEFAULT FALSE,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dashboard_widgets_tab
        FOREIGN KEY (tab_id)
        REFERENCES dashboard_tabs (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_dashboard_widgets_type
        CHECK (
            widget_type IN (
                'STAT',
                'STATUS',
                'TABLE',
                'LINE_CHART',
                'BAR_CHART',
                'PIE_CHART',
                'DONUT_CHART',
                'TEXT'
            )
        ),

    CONSTRAINT ck_dashboard_widgets_size
        CHECK (
            size IN (
                'SMALL',
                'MEDIUM',
                'LARGE',
                'FULL'
            )
        ),

    CONSTRAINT ck_dashboard_widgets_data_source_type
        CHECK (
            data_source_type IS NULL
            OR data_source_type IN (
                'MONITORING_RESULT',
                'MONITORING_JOB'
            )
        ),

    CONSTRAINT ck_dashboard_widgets_refresh_interval
        CHECK (
            refresh_interval IS NULL
            OR refresh_interval > 0
        ),

    CONSTRAINT ck_dashboard_widgets_refresh_unit
        CHECK (
            refresh_interval_unit IS NULL
            OR refresh_interval_unit IN (
                'SECONDS',
                'MINUTES'
            )
        )
);

CREATE INDEX idx_dashboard_widgets_tab
    ON dashboard_widgets (tab_id);

CREATE INDEX idx_dashboard_widgets_tab_sort
    ON dashboard_widgets (tab_id, sort_order);

CREATE INDEX idx_dashboard_widgets_enabled
    ON dashboard_widgets (enabled);


-- ============================================================
-- Dashboard Widget Result Snapshots
--
-- Used only when a widget has store_result = TRUE.
-- This is NOT a replacement for monitoring execution history.
-- ============================================================

CREATE TABLE dashboard_widget_results (
    id              BIGSERIAL PRIMARY KEY,
    widget_id       BIGINT NOT NULL,

    status          VARCHAR(20),
    message         TEXT,

    result_data     JSONB,

    result_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dashboard_widget_results_widget
        FOREIGN KEY (widget_id)
        REFERENCES dashboard_widgets (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_dashboard_widget_results_widget
    ON dashboard_widget_results (widget_id);

CREATE INDEX idx_dashboard_widget_results_widget_time
    ON dashboard_widget_results (widget_id, result_time DESC);