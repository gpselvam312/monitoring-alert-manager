# Database schema review

**Review baseline:** `feature/common-crud-ui-framework`  
**Migration inventory:** Flyway V1–V71  
**Scope:** migration SQL, JPA entities, repositories, services, controllers and the current templates.

This is a source-level schema review. It is not a live PostgreSQL catalog/data inspection; row counts and any unexpected foreign keys must be checked against the target database before applying destructive migrations.

## 1. Confirmed replacement history

| Earlier object | Replacement/current model | Evidence |
|---|---|---|
| `monitor_execution_history` | `monitoring_executions` + `monitoring_results` | V1 creates the old table; V11 creates the current execution/result tables. V11 does not copy the old history. No current entity maps the old table. |
| Initial alert/notification tables | New alert lifecycle and delivery schema | V44 renames the initial tables to `legacy_*` and creates the current alert model. |
| `dashboard_widget_results` | `monitoring_executions` + `monitoring_results` | V16 drops the duplicate dashboard result table. |
| Monitoring-job dashboard presentation columns | Dashboard widget configuration | V16 drops `dashboard_enabled`, `dashboard_title`, `dashboard_width`, and `dashboard_sort_order` from `monitoring_jobs`. |
| `machines.environment` | `machines.environment_id` | V9 adds the FK; V10 removes the old text column. |
| `schedules.timezone` | Server timezone | V18 removes the per-schedule timezone column. |
| `dashboard_widgets.store_result` | `monitoring_jobs.store_result` | V16 moves result storage ownership to the monitoring job. |
| `alert_rules.alert_message` | `alert_subject` and presentation configuration | V57 removes the old message column. |

## 2. Strong stale-table candidates

These objects have no current JPA entity mapping or active application workflow in the reviewed code. They are candidates for a forward cleanup migration; no drop migration has been added.

| Table | Origin / reason | Proposed action |
|---|---|---|
| `legacy_alert_rules` | V44 preserved the original alert-rule model; current `alert_rules` is the lifecycle model. | Drop after checking external/manual references. |
| `legacy_alert_states` | V44 preserved the old per-job alert state model. | Drop. |
| `legacy_alert_history` | V44 preserved the old alert history model; current `alert_history` is alert-lifecycle history. | Drop. |
| `legacy_notification_history` | V44 preserved the old notification history; current `notification_deliveries` is the delivery ledger. | Drop. |
| `legacy_notification_channels` | V44 preserved the initial channel model; current `notification_channels` and `notification_channel_secrets` are used. | Drop. |
| `legacy_recipients` | V44 preserved the initial recipient model; current `notification_recipients` is used. | Drop. |
| `legacy_monitor_notification_channels` | Old monitoring-job/channel join table, renamed by V44. | Drop. |
| `legacy_monitor_recipients` | Old monitoring-job/recipient join table, renamed by V44. | Drop. |
| `monitor_execution_history` | Superseded by V11; no current entity mapping or migration of its data. | Drop if the old execution history is no longer required. |
| `audit_log` | Created in V1; no current audit entity/service writes to it. The current retention-cleanup audit is `alert_cleanup_runs`; AUDIT_LOG menu entry was disabled in V66. | Drop if a general audit-log feature is not being retained. |

The legacy alert tables have foreign-key dependencies on one another. The cleanup migration must drop dependent tables first and must not use broad `CASCADE` as a shortcut. The expected child-first order is:

1. `legacy_notification_history`
2. `legacy_monitor_notification_channels`
3. `legacy_monitor_recipients`
4. `legacy_alert_rules`
5. `legacy_alert_states`
6. `legacy_alert_history`
7. `legacy_notification_channels`
8. `legacy_recipients`

The exact order will be validated against the target database's actual FK catalog before migration.

## 3. Redundant columns/tables now unused by application code

The application code has been changed to stop using these compatibility fields. Their physical removal is still pending a separately approved Flyway migration.

### 3.1 Singular alert-rule route targets

V59 copied old single targets into the many-to-many tables:

- `alert_rule_notification_route_recipients(route_id, recipient_id)`
- `alert_rule_notification_route_groups(route_id, group_id)`

The current route service writes and reads the junction tables, and the notification router no longer falls back to singular fields. The JPA mapping for the old fields has been removed.

**Candidate removal from `alert_rule_notification_routes`:**

- `recipient_id`
- `group_id`
- FK `fk_alert_rule_notification_routes_recipient`
- FK `fk_alert_rule_notification_routes_group`
- check constraint `chk_alert_rule_notification_routes_target`

V53 changed the old check constraint to permit recipientless Teams routes. That check is obsolete once both singular columns are removed.

Before dropping, compare the old scalar targets to the junction-table rows:

```sql
SELECT COUNT(*) AS missing_recipient_links
FROM ra_fcb.alert_rule_notification_routes r
WHERE r.recipient_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ra_fcb.alert_rule_notification_route_recipients x
      WHERE x.route_id = r.id AND x.recipient_id = r.recipient_id
  );

SELECT COUNT(*) AS missing_group_links
FROM ra_fcb.alert_rule_notification_routes r
WHERE r.group_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ra_fcb.alert_rule_notification_route_groups x
      WHERE x.route_id = r.id AND x.group_id = r.group_id
  );
```

V59 initially copied these values. The queries check whether any later/manual changes left scalar values that are not represented in the junction tables.

### 3.2 User/application membership join table

V25 backfilled `user_application_roles` from `user_applications`. The application now treats `user_application_roles` as the single source of application access. The redundant `User.applications` mapping and the writes to `user_applications` have been removed from application code.

**Candidate removal:** table `ra_fcb.user_applications`.

Preflight comparison:

```sql
SELECT COUNT(*) AS memberships_without_role_assignment
FROM ra_fcb.user_applications ua
LEFT JOIN ra_fcb.user_application_roles uar
  ON uar.user_id = ua.user_id
 AND uar.application_id = ua.application_id
WHERE uar.user_id IS NULL;

SELECT COUNT(*) AS role_assignments_without_legacy_membership
FROM ra_fcb.user_application_roles uar
LEFT JOIN ra_fcb.user_applications ua
  ON ua.user_id = uar.user_id
 AND ua.application_id = uar.application_id
WHERE ua.user_id IS NULL;
```

No current application feature reads this old table. Its rows are redundant membership data; the role and primary-application information lives in `user_application_roles`.

## 4. Tables/columns that should remain for now

| Object | Why it remains |
|---|---|
| `notification_policies`, `notification_routes`, `alert_rules.notification_policy_id` | The legacy policy/route administration UI and service still expose create/update/delete/toggle operations, and alert-rule management still validates/stores the policy reference. Runtime routing uses rule-specific routes, but this is not yet a retired UI feature. |
| `notification_channels.credential_reference` | Still identifies the logical secret key used by channel configuration and delivery. |
| `external_application_accounts.secret_reference` | Still supports externally managed secret references as an alternative to encrypted values. |
| `notification_deliveries.recipient_id` | Still identifies person-targeted deliveries; it is nullable for Teams deliveries. |
| `monitoring_jobs` distributed claim fields | Used by the repository's cross-instance execution-claim SQL. |
| `alert_rule_notification_route_recipients`, `alert_rule_notification_route_groups` | Current multi-recipient and multi-group routing model. |
| `alert_cleanup_runs` | Current retention cleanup audit. |
| `notification_channel_secrets` | Current encrypted channel secret storage. |

## 5. Schema/entity length and nullability drift to normalize

These mismatches are present between the baseline DDL and current entity/UI definitions. They should be resolved as one schema-contract change, with data-length preflight checks before narrowing database columns.

| Field | Baseline database definition | Current entity/UI definition |
|---|---|---|
| `users.full_name` | NOT NULL, VARCHAR(200) | Entity does not declare `nullable=false`. |
| `monitoring_jobs.name` | VARCHAR(200) | Entity and UI use 150. |
| `monitoring_jobs.description` | VARCHAR(1000) | Entity and UI use 500. |
| `machines.ip_address` | VARCHAR(45) | Entity declares length 50. |
| `schedules.name` | VARCHAR(150) | Entity and UI use 100. |
| `schedules.cron_expression` | VARCHAR(150) | Entity declares length 100. |

No data was truncated or schema columns changed as part of this review. The final direction for each mismatch will be recorded before adding the corresponding forward migration.

## 6. Proposed cleanup migration sequence

After approval of the exact destructive scope:

1. Drop the obsolete `legacy_*` tables in FK-safe order.
2. Drop `monitor_execution_history` and `audit_log`.
3. Drop `user_applications` after the role-assignment comparison.
4. Drop singular route target constraints/columns after the route-link comparison.
5. Normalize the documented length/nullability mismatches using explicit preflight checks.
6. Add migration tests for a clean database and a database upgraded through V71.

Existing Flyway migration files V1–V71 will remain unchanged. Cleanup will be implemented only as new forward migrations.
