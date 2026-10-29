-- Configurable subject and message templates for each alert rule.
ALTER TABLE ra_fcb.alert_rules
    ADD COLUMN alert_subject VARCHAR(500) NOT NULL DEFAULT '[{severity}] {ruleName}',
    ADD COLUMN alert_message TEXT NOT NULL DEFAULT 'Job {jobName} in application {applicationName}, environment {environment}: {fieldPath} evaluated to {actualValue} (threshold: {threshold}).';

COMMENT ON COLUMN ra_fcb.alert_rules.alert_subject IS
    'Subject template rendered when an alert is created.';
COMMENT ON COLUMN ra_fcb.alert_rules.alert_message IS
    'Message template rendered when an alert is created.';
