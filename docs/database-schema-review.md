# Database schema review

**Review baseline:** `feature/common-crud-ui-framework`  
**Migration inventory:** Flyway V1–V72  
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

## 2. Approved cleanup scope

The user approved the following exact destructive scope. V72 implements only these objects; it does not remove any additional tables or columns.

| Object | Reason | V72 action |
|---|---|---|
| `legacy_alert_rules` | V44 preserved the original alert-rule model; current `alert_rules` is the lifecycle model. | Drop |
| `legacy_alert_states` | V44 preserved the old per-job alert state model. | Drop |
| `legacy_alert_history` | V44 preserved the old alert history model; current `alert_history` is alert-lifecycle history. | Drop |
| `legacy_notification_history` | V44 preserved the old notification history; current `notification_deliveries` is the delivery ledger. | Drop |
| `legacy_notification_channels` | V44 preserved the initial channel model; current `notification_channels` and `notification_channel_secrets` are used. | Drop |
| `legacy_recipients` | V44 preserved the initial recipient model; current `notification_recipients` is used. | Drop |
| `legacy_monitor_notification_channels` | Old monitoring-job/channel join table, renamed by V44. | Drop |
| `legacy_monitor_recipients` | Old monitoring-job/recipient join table, renamed by V44. | Drop |
| `monitor_execution_history` | Superseded by V11; no current entity mapping or migration of its data. | Drop |
| `audit_log` | No current audit entity/service writes to it; the current retention-cleanup audit is `alert_cleanup_runs`. | Drop |
| `user_applications` | V25 backfilled `user_application_roles`; current application access uses the latter exclusively. | Drop after role-assignment preflight |
| `alert_rule_notification_routes.recipient_id`, `group_id` | V59 copied scalar values into current junction tables; application code no longer maps or uses the scalar fields. | Drop with the two FKs and obsolete target check constraint |

### 2.1 Migration safety checks

V72 performs the following checks before any destructive DDL and aborts the migration if any count is non-zero:

- A non-null scalar `recipient_id` has no matching row in `alert_rule_notification_route_recipients`.
- A non-null scalar `group_id` has no matching row in `alert_rule_notification_route_groups`.
- A `user_applications` membership has no corresponding `user_application_roles` assignment.

The migration then drops route constraints and scalar columns, the old membership table, the eight legacy tables, and the two obsolete V1 tables. Legacy tables are dropped child-first. Every drop is explicit and uses no `CASCADE`; any unexpected FK dependency causes the migration to fail rather than silently remove another object.

The migration is transactional under the normal PostgreSQL Flyway configuration. If a preflight or DDL statement fails, the migration is rolled back.

### 2.2 Explicitly excluded objects

The following objects are not part of the approved V72 scope and remain unchanged:

- `notification_policies`, `notification_routes`, and `alert_rules.notification_policy_id`; their administration UI/service operations still exist.
- `notification_channels.credential_reference` and `external_application_accounts.secret_reference`; both are active secret-reference fields.
- `notification_deliveries.recipient_id`; used for person-targeted deliveries and nullable for Teams.
- `monitoring_jobs` distributed claim fields, `streaming_job_claims`, and current alert-route recipient/group junction tables.
- `alert_cleanup_runs` and `notification_channel_secrets`.

No length/nullability normalization, data conversion, or other schema cleanup is included in V72.

## 3. Schema/entity length and nullability alignment

The following mappings were checked and corrected in entity metadata without changing physical database columns or truncating data:

| Field | Database definition | Current entity/UI definition |
|---|---|---|
| `users.full_name` | NOT NULL, VARCHAR(200) | Entity declares `nullable=false`, length 200. |
| `monitoring_jobs.name` | VARCHAR(200) | Entity matches 200; UI/service validation caps at 150. |
| `monitoring_jobs.description` | VARCHAR(1000) | Entity matches 1000; UI/service validation caps at 500. |
| `machines.ip_address` | VARCHAR(45) | Entity matches 45. |
| `schedules.name` | VARCHAR(150) | Entity matches 150; UI validation caps at 100. |
| `schedules.cron_expression` | VARCHAR(150) | Entity matches 150. |

The smaller UI/service limits are deliberate application-level validation rules.

## 4. Migration policy

- Existing Flyway migrations V1–V71 remain unchanged.
- V72 is a forward-only cleanup migration for the exact approved scope above.
- Do not use `CASCADE`; unexpected dependencies must be reviewed explicitly.
- Before deployment, run the V72 preflight queries against the target database and inspect the FK catalog for references to the legacy tables.
- This source review does not assert that a live database has been inspected or that V72 has been applied.
