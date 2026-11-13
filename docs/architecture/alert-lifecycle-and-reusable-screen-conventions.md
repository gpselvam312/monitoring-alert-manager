# Alert lifecycle and reusable screen conventions

## Alert lifecycle

- `alerts` remains the source of truth for one logical alert lifecycle.
- `alert_history` stores lifecycle and notification events.
- `alert_comments` stores append-only collaboration notes; comments do not change alert state.
- `user_alert_states` remains per-user read tracking. Opening an alert marks it read; no acknowledgement workflow is introduced.
- The display key `ALT-yyyy-########` is derived from the immutable alert creation year and database ID. The database primary key remains the internal relationship key.
- A resolved alert is retained in Alert History. A later trigger after recovery creates a new lifecycle and a new display key.

## Authorization boundary

Every alert list, summary count, detail lookup and comment write must be constrained by the signed-in user's `ALERT_VIEW` application assignments. UI filters are never treated as authorization. Application IDs supplied by the browser are validated against the access service before querying.

## Retention

- `RESOLVED_ALERTS` and `ALERT_HISTORY` policies support application, environment and alert-type scope.
- Alert source types are stored in the `alert_types` lookup table and ordered by database sort order. `ALL` is the retention wildcard; `MONITORING_JOB` is the initial configured source type.
- Other retention data types remain global until their own scoped cleanup queries are designed.
- Enabled policies with overlapping scopes are rejected to prevent competing cleanup windows.
- Resolved-alert retention cannot be shorter than overlapping lifecycle-history retention because deleting an alert also removes its lifecycle history, comments and per-user state.
- Active alerts and in-flight notification deliveries remain protected. Cleanup remains an explicit preview-and-confirm action.

## Reusable list and CRUD conventions

- Use `PageRequestSupport` for validated page sizes, sort-field whitelists, normalized sort direction and deterministic ID tie-breaking.
- Keep each feature's repository query and business service feature-specific; do not introduce generic inheritance that hides authorization or domain rules.
- Use `fragments/data-table.html` and `static/js/data-table.js` for AJAX-driven tables where the screen benefits from partial refresh.
- Use the shared application layout, Bootstrap card/table classes, consistent empty states and flash messages.
- List controllers should validate filter inputs, obtain accessible application IDs, call a paged repository query, and derive summary cards from the same authorization and non-status filters.
- Form controllers should validate referenced records server-side, delegate persistence and business rules to a service, and use Post/Redirect/Get after success.
- Keep audit fields and domain-specific validation in the feature service. Do not create a generic CRUD service that bypasses domain invariants.

## Future ServiceNow integration boundary

ServiceNow remains optional and is not part of alert lifecycle processing. When introduced, use a separate integration mapping keyed uniquely by the internal alert lifecycle ID, store the external incident ID and synchronization state, and make create/update/resolve operations idempotent. Before creating an incident, reuse an existing mapped incident that is still open/active. On recovery, resolve the mapped incident according to the configured ServiceNow workflow. Integration failures must not block alert creation, recovery or notifications.
