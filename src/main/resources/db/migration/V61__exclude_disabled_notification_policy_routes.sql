-- V52 originally copied routes for every notification policy.
-- Remove only the legacy policy-route copies belonging to disabled policies.
-- This preserves any distinct rule-specific routes that may already exist.
DELETE FROM ra_fcb.alert_rule_notification_routes arr
USING ra_fcb.alert_rules ar,
      ra_fcb.notification_policies np,
      ra_fcb.notification_routes nr
WHERE arr.alert_rule_id = ar.id
  AND np.id = ar.notification_policy_id
  AND np.enabled = FALSE
  AND nr.notification_policy_id = np.id
  AND arr.channel_id = nr.channel_id
  AND arr.recipient_id IS NOT DISTINCT FROM nr.recipient_id
  AND arr.group_id IS NOT DISTINCT FROM nr.group_id
  AND arr.severity = nr.severity
  AND arr.event_type = nr.event_type
  AND arr.enabled = nr.enabled
  AND arr.minimum_interval_seconds =
      CASE
          WHEN nr.event_type IN ('RECURRENCE', 'REMINDER')
               AND nr.minimum_interval_seconds = 0
          THEN 300
          ELSE nr.minimum_interval_seconds
      END;
