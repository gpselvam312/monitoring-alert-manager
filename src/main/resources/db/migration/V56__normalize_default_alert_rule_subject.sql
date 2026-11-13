UPDATE ra_fcb.alert_rules
SET alert_subject = '{severity} — {ruleName}'
WHERE alert_subject = '[{severity}] {ruleName}';
