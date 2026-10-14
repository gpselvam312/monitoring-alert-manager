# Dashboard Widget Consolidation Review and Implementation Plan

## Branch and baseline

- Working branch: `feature/dashboard-widget-consolidation`
- Base branch: `feature/monitoring-execution-scheduling`
- Base commit includes merged response-parser configuration GUI PR #13.
- This branch is deliberately separate; do not merge it until implementation and validation are complete.

## Widget inventory

The current validator accepts these persisted widget types: `STAT`, `STATUS`, `TABLE`, `SUMMARY_TABLE`, `CHART`, `TEXT`, `SYSTEM_METRICS`, and `ELASTICSEARCH`. Kafka consumer health is implemented as a specialized renderer within the generic `TABLE` type (renderer/config value `KAFKA_CONSUMER_HEALTH`), rather than as a separate top-level widget type.

| Widget type / renderer | Main purpose | Consolidation recommendation |
|---|---|---|
| `STAT` | Single value / KPI | Keep as a lightweight preset or implement as a Summary Table card-only configuration. Do not remove until existing widget configs are migrated and parity verified. |
| `STATUS` | Health/status indicator | Keep as a simple status preset initially. A common mapping/status engine can be shared with Summary Table, but the concise status-only presentation remains useful. |
| `TABLE` | General mapped tabular data; includes specialized Kafka renderer | Keep the generic table renderer during migration. The standard row/table presentation can converge on Summary Table, but preserve renderer-specific logic and existing configurations until tests demonstrate parity. |
| `SUMMARY_TABLE` | Configurable cards + detail table, grouping, sorting, paging, conditional formatting, row status, totals | Recommended common configurable table/card renderer and long-term default for tabular monitoring data. |
| `CHART` | Time/category/series visualization | Keep. A table/card renderer is not a substitute for a chart. Share response parsing and field mapping where practical. |
| `TEXT` | Text / message display | Keep as a presentation preset; can share parser and error-state infrastructure. |
| `SYSTEM_METRICS` | Specialized server metrics/health collection, including interpretation and thresholds | Keep its data interpretation and health-ranking logic. Consider reusing Summary Table presentation only after metrics semantics and completeness rules are preserved. |
| `ELASTICSEARCH` | Specialized cluster/node health and metrics layout | Keep specialized processing. Consolidate presentation only if cluster health, node metrics, and alerts can be represented without losing required semantics. |

## Current findings

1. `dashboard.js` forces `SYSTEM_METRICS`, `ELASTICSEARCH`, and `SUMMARY_TABLE` to `FULL` width instead of honoring the saved `widget.size`. This is a direct cause of the size selector appearing ineffective.
2. `summary-table-configurator.js` defaulted the “summary card filters rows when clicked” flag to enabled when there was no saved card configuration. The default is now off; a saved explicit true remains true.
3. `summary-table.css` had status-row coloring but no neutral zebra striping for ordinary detail rows. Zebra striping has been restored with theme variables and status-row exclusions.
4. Dashboard Tab and Dashboard Widget use Bootstrap modals in `administration/dashboard-configuration.html`. Responsive sizing and body scrolling rules have been added in `app.css`; browser verification is still required.
5. Summary Table rendering already supports summary cards independently from detail rendering when `details.enabled === false`. The configurator still needs an obvious, tested UI control for cards-only mode.
6. Summary Table has configurable `summary.cards`, total-row handling, grouped summary cards, field mapping, search, sorting, pagination, group-by, row status, numeric thresholds, and conditional formatting. Existing documented parser formats are AUTO, JSON, PLAIN_TEXT, DELIMITED, KEY_VALUE, and REGEX.
7. Boolean display should remain controlled by the existing per-column format selector; no separate global boolean display option is needed. Optional icons belong to individual summary cards.
8. Rows / collection discovery is currently populated from discovered arrays/object collections. Confirm whether the reported duplicate choices are distinct paths with identical labels/row counts or genuinely duplicate entries; do not remove a choice solely because its labels look alike.
9. Summary card click handling currently has in-widget filter support. Cross-widget targeting should be an explicit, opt-in action with a validated target widget ID; do not infer a target from DOM order or click position.

## Recommended target configuration model

Keep the existing persisted `fieldConfigJson` contract backwards-compatible while progressively adding these optional properties:

- `rowPath`: path to an array of row objects.
- `responseParser`: existing parser type and parser-specific options.
- `details.enabled`: show/hide detail table; false supports cards-only layout.
- `details.columns[]`: `field`, `label`, visibility, sorting/search/group flags, display format, optional icon, and boolean display labels.
- `summary.cards[]`: card label, field/aggregation, display format/icon, filterability and action.
- `summary.cards[].action`: `FILTER_THIS_WIDGET` (default), `TARGET_WIDGET` with an explicit target ID and target filter field/value, or `NONE`.
- `summary.thresholds` and `details.rowStatus`: configurable status/threshold mapping; keep raw source values unchanged.
- `details.pagination`, `details.sorting`, `details.search`, `details.groupBy`, and conditional formatting: preserve existing options.
- Boolean display remains governed by each column's existing format setting; do not introduce a second global boolean-format configuration.

Use a single parsing/field-mapping path across generic Table, Summary Table, Kafka and Elasticsearch, but keep domain-specific interpretation adapters. Kafka must retain consumer pagination, default highest-lag-first ordering, configured warning/critical thresholds, and correct lag/health calculations. Elasticsearch must retain cluster/node health semantics and required metrics.

## Planned implementation order

### Phase 1 — usability and styling
- [x] Restore zebra striping for ordinary Summary Table rows while preserving status-row colors.
- [x] Default card filtering to off for newly configured summary cards; preserve explicitly saved settings.
- [x] Honor configured dashboard widget size rather than forcing selected types to full width.
- [x] Improve Dashboard Tab / Widget modal sizing and scrolling CSS.
- [ ] Verify light/dark themes, status rows, hover and selected states in the browser (CSS updated; visual verification not yet run).
- [ ] Add select-all row selection only after defining what selected rows enable; recommended scope is the current displayed page, with a clear label and indeterminate state.

### Phase 2 — Summary Table configurator
- [x] Add an explicit “Show detail table” option, default true, to enable cards-only dashboards. Persist as `details.enabled`; browser round-trip verification remains.
- [x] Make the card-click option dependent on Summary Card selection. When Summary Card is off, disable and clear card-click filtering; selecting Summary Card leaves filtering unchecked by default. Saved explicit settings are retained.
- [ ] Add per-card action: filter this widget (default), target a selected widget, or no action. Target selection must exclude self, validate target compatibility, persist and restore.
- [x] Add optional icons for summary cards using the existing Bootstrap Icons library and a restricted icon-name format. Table-header icons are not part of this request.
- [x] Add configurable boolean labels and display modes (labels, badge, icons, raw) for boolean table cells; raw source values remain the basis for filters and thresholds. Summary-card boolean presentation still needs review.
- [ ] Investigate the Rows / collection choices: use path as identity, show meaningful path labels, and avoid collapsing distinct paths merely because their row shapes are alike.
- [ ] Add select-all row checkbox only with defined row-selection behavior; default to visible-page rows, not all unseen pages.

### Phase 3 — renderer consolidation
- [ ] Add parity tests for generic `TABLE` and Summary Table, including saved legacy configurations.
- [ ] Reuse Summary Table presentation for generic table configurations only after migrating/normalizing column mappings and verifying sorting, search, paging, thresholds, and existing field labels.
- [ ] Keep Kafka-specific data interpretation and default lag sorting; render its normalized row model through the common table only after parity tests.
- [ ] Keep Elasticsearch and System Metrics interpretation separate; evaluate optional common presentation only after node/metric/status parity tests.
- [ ] Retain STAT/STATUS/TEXT/CHART presets unless migration provides a clear usability benefit and no loss of current behavior.
- [ ] Add a Flyway migration only if persisted widget types or JSON configs actually need transformation. Never remove a persisted type from validation before old configurations have a safe migration or compatibility path.

### Later milestone — common widget error handling (deferred)
Do not implement this in the current styling/configuration pass. After consolidation work, add a shared widget failure-state pattern:
- show a short friendly message inside only the failed widget (for example, “Invalid response format.” or “Unable to retrieve monitoring data.”);
- log technical exception details server-side, never expose stack traces, script text, exception internals or sensitive payloads in the dashboard;
- isolate failure so other widgets continue rendering;
- do not interpret invalid or incomplete data as healthy/critical;
- recover on a later successful refresh;
- test malformed JSON, unexpected shape, parser failure, missing fields, mixed success/failure across widgets, and recovery.

## Validation required before merge

- Java/Maven test suite and relevant JS syntax/lint checks, if configured.
- Summary Table unit/integration tests for filtering defaults, saved-config round trip, card-only mode, boolean labels, icon validation and card actions.
- Kafka regression tests for pagination, descending lag sort, thresholds and status mapping.
- Elasticsearch/System Metrics regression tests for required metrics and health interpretation.
- UI checks in light and dark themes, at desktop and narrow viewport widths, including both modals.
- Confirm old `TABLE`, `STAT`, `STATUS`, `TEXT`, `CHART`, `SYSTEM_METRICS`, and `ELASTICSEARCH` configurations continue to load.
- Verify branch diff and workflow/test results before opening or merging a PR.
