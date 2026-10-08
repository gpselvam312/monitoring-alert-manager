-- ============================================================
-- Monitoring Alert Manager
-- Initial database schema
-- PostgreSQL
-- ============================================================

-- ============================================================
-- USERS / ROLES
-- ============================================================

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(100) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    email               VARCHAR(255),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(50) NOT NULL UNIQUE,
    description         VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id             BIGINT NOT NULL,
    role_id             BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE CASCADE
);

-- ============================================================
-- MACHINES / SERVERS
-- ============================================================

CREATE TABLE machines (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL UNIQUE,
    hostname            VARCHAR(255),
    ip_address          VARCHAR(45),
    environment         VARCHAR(50),
    description         VARCHAR(500),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT fk_machines_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id),

    CONSTRAINT fk_machines_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id)
);

-- ============================================================
-- SCHEDULING
-- ============================================================

CREATE TABLE schedules (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL UNIQUE,
    description         VARCHAR(500),
    schedule_type       VARCHAR(30) NOT NULL,
    cron_expression     VARCHAR(150),
    fixed_delay_seconds INTEGER,
    fixed_rate_seconds  INTEGER,
    start_time          TIME,
    end_time            TIME,
    timezone            VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_schedule_type
        CHECK (schedule_type IN ('CRON', 'FIXED_DELAY', 'FIXED_RATE')),

    CONSTRAINT chk_cron_schedule
        CHECK (
            schedule_type <> 'CRON'
            OR cron_expression IS NOT NULL
        ),

    CONSTRAINT chk_fixed_delay_schedule
        CHECK (
            schedule_type <> 'FIXED_DELAY'
            OR fixed_delay_seconds IS NOT NULL
        ),

    CONSTRAINT chk_fixed_rate_schedule
        CHECK (
            schedule_type <> 'FIXED_RATE'
            OR fixed_rate_seconds IS NOT NULL
        )
);

-- ============================================================
-- MONITORING JOBS
-- ============================================================

CREATE TABLE monitoring_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL UNIQUE,
    description         VARCHAR(1000),

    job_type            VARCHAR(50) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    severity            VARCHAR(20) NOT NULL DEFAULT 'CRITICAL',

    machine_id          BIGINT,
    schedule_id         BIGINT,

    timeout_seconds     INTEGER NOT NULL DEFAULT 30,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    retry_delay_seconds INTEGER NOT NULL DEFAULT 5,

    expected_result     TEXT,
    failure_message     TEXT,

    recovery_enabled    BOOLEAN NOT NULL DEFAULT TRUE,

    -- Script monitoring
    script_path         VARCHAR(1000),
    working_directory   VARCHAR(1000),
    command_arguments   TEXT,

    -- API monitoring
    http_method         VARCHAR(20),
    url                 VARCHAR(2000),
    request_headers     TEXT,
    request_body        TEXT,
    expected_http_status INTEGER,
    expected_response   TEXT,

    -- Health check monitoring
    health_check_type   VARCHAR(20),
    target_host         VARCHAR(255),
    target_port         INTEGER,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT fk_monitoring_jobs_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(id),

    CONSTRAINT fk_monitoring_jobs_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES schedules(id),

    CONSTRAINT fk_monitoring_jobs_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id),

    CONSTRAINT fk_monitoring_jobs_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id),

    CONSTRAINT chk_monitoring_job_type
        CHECK (
            job_type IN (
                'SCRIPT',
                'API',
                'HEALTH_CHECK'
            )
        ),

    CONSTRAINT chk_monitoring_job_severity
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'CRITICAL'
            )
        )
);

-- ============================================================
-- ALERT RULES
-- ============================================================

CREATE TABLE alert_rules (
    id                  BIGSERIAL PRIMARY KEY,
    monitoring_job_id   BIGINT NOT NULL,

    consecutive_failures INTEGER NOT NULL DEFAULT 1,
    alert_enabled      BOOLEAN NOT NULL DEFAULT TRUE,

    cooldown_seconds   INTEGER NOT NULL DEFAULT 0,
    reminder_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_interval_seconds INTEGER,

    recovery_enabled   BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_rules_monitoring_job
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id)
        ON DELETE CASCADE
);

-- ============================================================
-- ALERT STATE
-- One current state per monitoring job
-- ============================================================

CREATE TABLE alert_states (
    id                  BIGSERIAL PRIMARY KEY,
    monitoring_job_id   BIGINT NOT NULL UNIQUE,

    current_status      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    previous_status     VARCHAR(20),

    failure_count       INTEGER NOT NULL DEFAULT 0,

    first_failure_at    TIMESTAMP WITH TIME ZONE,
    last_failure_at     TIMESTAMP WITH TIME ZONE,
    last_alert_at       TIMESTAMP WITH TIME ZONE,
    recovered_at        TIMESTAMP WITH TIME ZONE,

    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_states_monitoring_job
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_alert_state_status
        CHECK (
            current_status IN (
                'UNKNOWN',
                'HEALTHY',
                'WARNING',
                'CRITICAL'
            )
        )
);

-- ============================================================
-- NOTIFICATION CHANNELS
-- ============================================================

CREATE TABLE notification_channels (
    id                  BIGSERIAL PRIMARY KEY,

    name                VARCHAR(100) NOT NULL UNIQUE,
    channel_type        VARCHAR(30) NOT NULL,

    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    configuration       TEXT,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_notification_channel_type
        CHECK (
            channel_type IN (
                'EMAIL',
                'TEAMS',
                'WHATSAPP'
            )
        )
);

-- ============================================================
-- RECIPIENTS
-- ============================================================

CREATE TABLE recipients (
    id                  BIGSERIAL PRIMARY KEY,

    name                VARCHAR(200) NOT NULL,
    email               VARCHAR(255),
    phone_number        VARCHAR(50),
    teams_address       VARCHAR(1000),

    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- MONITOR -> NOTIFICATION CHANNEL
-- ============================================================

CREATE TABLE monitor_notification_channels (
    monitoring_job_id   BIGINT NOT NULL,
    notification_channel_id BIGINT NOT NULL,

    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (
        monitoring_job_id,
        notification_channel_id
    ),

    CONSTRAINT fk_monitor_channels_monitor
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_monitor_channels_channel
        FOREIGN KEY (notification_channel_id)
        REFERENCES notification_channels(id)
        ON DELETE CASCADE
);

-- ============================================================
-- MONITOR -> RECIPIENT
-- ============================================================

CREATE TABLE monitor_recipients (
    monitoring_job_id   BIGINT NOT NULL,
    recipient_id        BIGINT NOT NULL,

    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (
        monitoring_job_id,
        recipient_id
    ),

    CONSTRAINT fk_monitor_recipients_monitor
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_monitor_recipients_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES recipients(id)
        ON DELETE CASCADE
);

-- ============================================================
-- ALERT HISTORY
-- ============================================================

CREATE TABLE alert_history (
    id                  BIGSERIAL PRIMARY KEY,

    monitoring_job_id   BIGINT NOT NULL,

    alert_type          VARCHAR(20) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    status              VARCHAR(30) NOT NULL,

    title               VARCHAR(500),
    message             TEXT,

    expected_result     TEXT,
    actual_result       TEXT,

    failure_count       INTEGER,

    triggered_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_alert_history_monitoring_job
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id),

    CONSTRAINT chk_alert_history_type
        CHECK (
            alert_type IN (
                'FAILURE',
                'RECOVERY',
                'REMINDER'
            )
        ),

    CONSTRAINT chk_alert_history_status
        CHECK (
            status IN (
                'CREATED',
                'SENT',
                'PARTIALLY_SENT',
                'FAILED',
                'RESOLVED'
            )
        )
);

-- ============================================================
-- NOTIFICATION HISTORY
-- ============================================================

CREATE TABLE notification_history (
    id                  BIGSERIAL PRIMARY KEY,

    alert_history_id    BIGINT NOT NULL,
    notification_channel_id BIGINT NOT NULL,
    recipient_id        BIGINT,

    status              VARCHAR(30) NOT NULL,

    provider_message_id VARCHAR(500),
    error_message       TEXT,

    attempt_count       INTEGER NOT NULL DEFAULT 0,

    sent_at             TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_history_alert
        FOREIGN KEY (alert_history_id)
        REFERENCES alert_history(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notification_history_channel
        FOREIGN KEY (notification_channel_id)
        REFERENCES notification_channels(id),

    CONSTRAINT fk_notification_history_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES recipients(id),

    CONSTRAINT chk_notification_history_status
        CHECK (
            status IN (
                'PENDING',
                'SENT',
                'FAILED',
                'SKIPPED'
            )
        )
);

-- ============================================================
-- MONITOR EXECUTION HISTORY
-- ============================================================

CREATE TABLE monitor_execution_history (
    id                  BIGSERIAL PRIMARY KEY,

    monitoring_job_id   BIGINT NOT NULL,

    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,

    duration_ms         BIGINT,

    status              VARCHAR(30) NOT NULL,

    exit_code           INTEGER,
    http_status         INTEGER,

    expected_result     TEXT,
    actual_result       TEXT,

    output              TEXT,
    error_output        TEXT,

    error_message       TEXT,

    attempt_number      INTEGER NOT NULL DEFAULT 1,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_execution_history_monitor
        FOREIGN KEY (monitoring_job_id)
        REFERENCES monitoring_jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_execution_history_status
        CHECK (
            status IN (
                'RUNNING',
                'SUCCESS',
                'WARNING',
                'FAILED',
                'TIMEOUT',
                'ERROR'
            )
        )
);

-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_log (
    id                  BIGSERIAL PRIMARY KEY,

    user_id             BIGINT,

    action              VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(100),
    entity_id           BIGINT,

    old_value           TEXT,
    new_value           TEXT,

    ip_address          VARCHAR(45),

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX idx_monitoring_jobs_machine
    ON monitoring_jobs(machine_id);

CREATE INDEX idx_monitoring_jobs_schedule
    ON monitoring_jobs(schedule_id);

CREATE INDEX idx_monitoring_jobs_enabled
    ON monitoring_jobs(enabled);

CREATE INDEX idx_alert_history_monitor
    ON alert_history(monitoring_job_id);

CREATE INDEX idx_alert_history_status
    ON alert_history(status);

CREATE INDEX idx_alert_history_triggered
    ON alert_history(triggered_at);

CREATE INDEX idx_notification_history_alert
    ON notification_history(alert_history_id);

CREATE INDEX idx_notification_history_status
    ON notification_history(status);

CREATE INDEX idx_notification_history_created
    ON notification_history(created_at);

CREATE INDEX idx_execution_history_monitor
    ON monitor_execution_history(monitoring_job_id);

CREATE INDEX idx_execution_history_started
    ON monitor_execution_history(started_at);

CREATE INDEX idx_execution_history_status
    ON monitor_execution_history(status);

CREATE INDEX idx_audit_log_created
    ON audit_log(created_at);

CREATE INDEX idx_audit_log_user
    ON audit_log(user_id);

-- ============================================================
-- INITIAL ROLES
-- ============================================================

INSERT INTO roles (name, description)
VALUES
    ('ADMIN', 'Full system administration access'),
    ('OPERATOR', 'Monitoring and alert operations access'),
    ('VIEWER', 'Read-only dashboard and monitoring access');
