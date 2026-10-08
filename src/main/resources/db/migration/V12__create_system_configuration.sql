-- ============================================================
-- V12 - System Configuration and Administration Foundation
-- ============================================================

-- ============================================================
-- 1. SYSTEM SETTINGS
-- ============================================================

CREATE TABLE ra_fcb.system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT system_settings_key_unique UNIQUE (setting_key),
    CONSTRAINT system_settings_data_type_chk
        CHECK (data_type IN ('STRING', 'BOOLEAN', 'INTEGER', 'JSON')),
    CONSTRAINT fk_system_settings_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),
    CONSTRAINT fk_system_settings_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

-- ============================================================
-- 2. APPLICATION BRANDING
-- ============================================================

CREATE TABLE ra_fcb.application_branding (
    id BIGSERIAL PRIMARY KEY,
    logo_filename VARCHAR(255),
    logo_content_type VARCHAR(100),
    logo_path VARCHAR(500),
    logo_version BIGINT NOT NULL DEFAULT 1,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT fk_application_branding_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),
    CONSTRAINT fk_application_branding_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

-- Allow only one branding configuration row.
CREATE UNIQUE INDEX ux_application_branding_singleton
    ON ra_fcb.application_branding ((1));

-- ============================================================
-- 3. EMAIL CONFIGURATION
-- ============================================================

CREATE TABLE ra_fcb.email_config (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(30),
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    username VARCHAR(255),
    password TEXT,
    encryption VARCHAR(10),
    from_name VARCHAR(255),
    from_email VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT fk_email_config_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),
    CONSTRAINT fk_email_config_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

-- Only one global email configuration is allowed.
CREATE UNIQUE INDEX ux_email_config_singleton
    ON ra_fcb.email_config ((1));

-- ============================================================
-- 4. WHATSAPP CONFIGURATION
-- ============================================================

CREATE TABLE ra_fcb.whatsapp_config (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(30),
    api_base_url VARCHAR(500),
    waba_id VARCHAR(255),
    phone_number_id VARCHAR(255),
    access_token TEXT,
    template_namespace VARCHAR(255),
    template_name VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT fk_whatsapp_config_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),
    CONSTRAINT fk_whatsapp_config_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

-- Only one global WhatsApp configuration is allowed.
CREATE UNIQUE INDEX ux_whatsapp_config_singleton
    ON ra_fcb.whatsapp_config ((1));

-- ============================================================
-- 5. TEAMS CONFIGURATION
-- ============================================================

CREATE TABLE ra_fcb.teams_config (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(30),
    name VARCHAR(255),
    description VARCHAR(500),
    webhook_url TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT fk_teams_config_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),
    CONSTRAINT fk_teams_config_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

-- Only one global Teams configuration is allowed.
CREATE UNIQUE INDEX ux_teams_config_singleton
    ON ra_fcb.teams_config ((1));

-- ============================================================
-- 6. MENU ITEMS
-- ============================================================

CREATE TABLE ra_fcb.menu_items (
    id BIGSERIAL PRIMARY KEY,
    menu_key VARCHAR(100) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    url VARCHAR(255),
    icon VARCHAR(100),
    parent_id BIGINT,
    display_order INTEGER NOT NULL DEFAULT 100,
    required_permission VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,

    CONSTRAINT menu_items_key_unique UNIQUE (menu_key),

    CONSTRAINT fk_menu_items_parent
        FOREIGN KEY (parent_id)
        REFERENCES ra_fcb.menu_items(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_menu_items_created_by
        FOREIGN KEY (created_by) REFERENCES ra_fcb.users(id),

    CONSTRAINT fk_menu_items_updated_by
        FOREIGN KEY (updated_by) REFERENCES ra_fcb.users(id)
);

CREATE INDEX ix_menu_items_parent_order
    ON ra_fcb.menu_items (parent_id, display_order);

CREATE INDEX ix_menu_items_required_permission
    ON ra_fcb.menu_items (required_permission);

-- ============================================================
-- 7. INITIAL SYSTEM SETTINGS
-- ============================================================

INSERT INTO ra_fcb.system_settings
    (setting_key, setting_value, data_type, description)
VALUES
    (
        'APPLICATION_NAME',
        'Monitoring Alert Manager',
        'STRING',
        'Global application name displayed in the application header and browser title.'
    ),
    (
        'THEME',
        'orange',
        'STRING',
        'Global application theme. Supported values: orange, blue, green, purple.'
    ),
    (
        'SITE_OFFLINE',
        'false',
        'BOOLEAN',
        'Places the application into maintenance/offline mode when enabled.'
    ),
    (
        'MAINTENANCE_MESSAGE',
        'Site is currently under maintenance. Please try again later.',
        'STRING',
        'Message displayed to users when the site is offline.'
    ),
    (
        'MAINTENANCE_BYPASS_IPS',
        '[]',
        'JSON',
        'IP addresses allowed to access the application while maintenance mode is enabled.'
    );

-- ============================================================
-- 8. INITIAL MENU ITEMS
-- ============================================================

-- Top-level menus
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, display_order, required_permission)
VALUES
    (
        'DASHBOARD',
        'Dashboard',
        '/dashboard',
        'bi-speedometer2',
        10,
        'MONITORING_VIEW'
    ),
    (
        'MONITORING_JOBS',
        'Monitoring Jobs',
        '/monitoring/jobs',
        'bi-activity',
        20,
        'MONITORING_VIEW'
    ),
    (
        'MACHINES',
        'Machines',
        '/machines',
        'bi-pc-display',
        30,
        'MACHINE_VIEW'
    ),
    (
        'SCHEDULES',
        'Schedules',
        '/schedules',
        'bi-calendar3',
        40,
        'SCHEDULE_VIEW'
    ),
    (
        'ALERTS',
        'Alerts',
        NULL,
        'bi-bell',
        50,
        'ALERT_VIEW'
    ),
    (
        'ADMINISTRATION',
        'Administration',
        NULL,
        'bi-gear',
        60,
        'SYSTEM_CONFIG'
    );

-- Alert children
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'ACTIVE_ALERTS',
    'Active Alerts',
    '/alerts',
    'bi-bell',
    id,
    10,
    'ALERT_VIEW'
FROM ra_fcb.menu_items
WHERE menu_key = 'ALERTS';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'ALERT_HISTORY',
    'Alert History',
    '/alerts/history',
    'bi-clock-history',
    id,
    20,
    'ALERT_VIEW'
FROM ra_fcb.menu_items
WHERE menu_key = 'ALERTS';

-- Administration children
INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'APPLICATIONS',
    'Applications',
    '/administration/applications',
    'bi-boxes',
    id,
    10,
    'SYSTEM_CONFIG'
FROM ra_fcb.menu_items
WHERE menu_key = 'ADMINISTRATION';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'ENVIRONMENTS',
    'Environments',
    '/administration/environments',
    'bi-diagram-3',
    id,
    20,
    'SYSTEM_CONFIG'
FROM ra_fcb.menu_items
WHERE menu_key = 'ADMINISTRATION';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'USERS',
    'Users',
    '/administration/users',
    'bi-people',
    id,
    30,
    'USER_CONFIG'
FROM ra_fcb.menu_items
WHERE menu_key = 'ADMINISTRATION';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'SETTINGS',
    'Settings',
    '/administration/settings',
    'bi-sliders',
    id,
    40,
    'SYSTEM_CONFIG'
FROM ra_fcb.menu_items
WHERE menu_key = 'ADMINISTRATION';

INSERT INTO ra_fcb.menu_items
    (menu_key, menu_name, url, icon, parent_id, display_order, required_permission)
SELECT
    'AUDIT_LOG',
    'Audit Log',
    '/administration/audit',
    'bi-journal-text',
    id,
    50,
    'AUDIT_VIEW'
FROM ra_fcb.menu_items
WHERE menu_key = 'ADMINISTRATION';

