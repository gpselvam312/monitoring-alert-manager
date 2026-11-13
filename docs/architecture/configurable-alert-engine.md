# Configurable Alert Engine — Architecture and Configuration Contract

**Status:** Proposed implementation baseline  
**Branch:** `feature/configurable-alert-engine`  
**Configuration schema version:** 1

## 1. Design principles

- The alert engine evaluates monitoring execution JSON without technology-specific logic.
- Platform metadata and monitoring payload are both addressable, but have separate namespaces.
- Detection, lifecycle, and presentation configuration are independent.
- Existing alert lifecycle, routing, notification delivery, recovery, and retention behavior must remain intact.
- Configuration is validated before persistence and evaluated with bounded resource usage.
- Existing rules remain readable during migration; no destructive replacement of current rule behavior.

## 2. Monitoring response contract

The engine accepts the complete execution result JSON. It does not require monitoring producers to rewrite their payload into a Kafka-, database-, or API-specific model.

Two logical namespaces are exposed to configuration:

- `response`: the complete root document and platform-level metadata.
- `data`: the monitoring payload, when the producer supplies a top-level `data` object.

For compatibility, if the incoming document has no top-level `data` property, the complete document is exposed through `data` as well. This keeps existing flat JSON responses usable.

Example:

```json
{
  "schemaVersion": 1,
  "checkType": "KAFKA_HEALTH",
  "checkedAt": "2026-10-11T10:30:00+05:30",
  "status": "CRITICAL",
  "summary": {
    "consumerGroupCount": 12
  },
  "data": {
    "cluster": {
      "name": "kafka-cluster-sit"
    },
    "consumerGroups": {
      "items": []
    }
  }
}
```

Example field references:

- `response.status`
- `response.checkedAt`
- `response.summary.consumerGroupCount`
- `data.cluster.name`
- `data.consumerGroups.items`

The root document is always available as `response`. The `data` namespace is a view of the explicit `data` property when present, otherwise a compatibility view of the whole document. Namespace resolution must be explicit and deterministic; it must not silently fall back from a missing path to a similarly named path in another namespace.

A future producer contract may standardize `schemaVersion`, `checkType`, `checkedAt`, `status`, `summary`, and `data`, but these fields are not mandatory for the engine to parse arbitrary JSON.

## 3. Condition configuration

Detection and recovery use the same versioned condition-tree model. The rule stores separate trees for detection and recovery.

Supported node types:

- `GROUP`: nested `AND` / `OR` groups.
- `CONDITION`: scalar comparison against a literal or typed configured value.
- `FIELD_COMPARISON`: compare one resolved field with another resolved field.
- `COLLECTION`: evaluate an array using `ANY`, `ALL`, or `NONE`.
- `AGGREGATE`: calculate `COUNT`, `SUM`, `MIN`, or `MAX` over a collection, optionally after filtering.

Operators are type-aware:

- Numeric: `GT`, `GTE`, `LT`, `LTE`.
- Equality: `EQ`, `NE`, `EQ_IGNORE_CASE`.
- String: `CONTAINS`, `NOT_CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`.
- Boolean: `IS_TRUE`, `IS_FALSE`.
- Presence: `EXISTS`, `NOT_EXISTS`, `IS_NULL`, `IS_NOT_NULL`.

Evaluation semantics:

- Missing, null, and wrong-type values are distinct states.
- Numeric operators never coerce invalid strings to zero.
- `ALL` over an empty collection is false by default; empty-collection semantics must be explicit in the evaluator contract and tests.
- A collection predicate is evaluated against each element's own context, so field comparisons remain within the same element.
- Regex and traversal are bounded; no scripting or arbitrary code execution is permitted.
- Evaluation returns a structured explanation containing outcome, resolved values where safe, and validation/runtime errors.

## 4. Presentation configuration

Presentation is independent of detection and stored as a versioned document.

Supported section types:

- `TEXT`
- `KEY_VALUE`
- `TABLE`
- `LIST`
- `JSON`

A section may define an identifier, title, order, source path, selected fields/columns, labels, filters, sorting, maximum rows, empty-state text, visibility condition, and template.

A shared presentation model is built once from the execution payload and alert context. Channel renderers consume that model:

- Email: escaped HTML, key/value blocks, tables and lists.
- Teams: Adaptive Cards with supported card layout primitives.
- WhatsApp: plain text with configurable character and row limits and an explicit truncation indicator.

Channel renderers must not independently interpret the raw condition or presentation JSON.

## 5. Persistence and compatibility

Target rule configuration fields:

- `condition_config JSONB`
- `recovery_config JSONB`
- `presentation_config JSONB`
- `config_version INTEGER NOT NULL DEFAULT 1`

These are proposed additions to the existing alert rule model, not a replacement of alert, history, evaluation, route, or delivery tables.

Compatibility rules:

1. Existing rule columns and lifecycle semantics remain available during migration.
2. Existing condition definitions are translated to the new condition model through a deterministic migration/adapter.
3. Existing subject/message templates remain a legacy presentation fallback until each rule has a validated presentation configuration.
4. A rule is not switched to the new evaluator until its translated configuration passes validation.
5. Migration must be reversible where practical and must not delete original configuration data.

Final column names and migration mechanics must be checked against the current entity mappings and Flyway history before creating the migration.

## 6. Java component boundaries

The existing package structure will be respected and extended rather than mechanically reorganized.

Responsibilities:

- JSON document/path layer: parsing, namespace resolution, typed values, discovery.
- Condition engine: node dispatch, groups, scalar comparisons, collection scope, aggregation.
- Evaluation orchestrator: execution guards, rule selection, invoking detection/recovery, lifecycle coordination.
- Lifecycle services: correlation, recurrence, recovery and history.
- Presentation engine: evaluate configured sections and create a channel-neutral model.
- Channel renderers: convert the shared model to email HTML, Teams Adaptive Cards, or WhatsApp text.

The current `AlertEvaluationService` must not retain JSON traversal, every operator implementation, presentation rendering, and lifecycle orchestration in one class.

## 7. Validation and operational limits

Initial limits must be configurable and have safe defaults. At minimum, the engine must bound:

- JSON document size and nesting depth.
- Number of visited array elements.
- Condition tree depth and node count.
- Regex input length and execution behavior.
- Presentation section count and rendered row count.
- Final message size per notification channel.

Save-time validation checks schema version, node shape, required fields, operator/type compatibility, path syntax, collection source type where sample data is available, and template placeholders. Runtime failures are recorded as evaluation errors; they must not be converted into a match or a recovery.

## 8. Delivery sequence

1. Reconcile this contract with current entities, migrations, evaluation tests, and notification contracts.
2. Implement JSON namespace/path resolution and typed field discovery.
3. Implement the generic condition evaluator with collection scope and aggregation.
4. Add configuration validation and backward-compatible rule adaptation.
5. Add presentation model and channel renderers.
6. Update the rule UI with friendly field selection and advanced configuration.
7. Add integration, migration, compatibility, and load-boundary tests.

No Kafka-specific code belongs in the core evaluator. Kafka is one fixture/test payload among multiple monitoring examples.


## Versioned presentation configuration

\`\`\`json
{
  "version": 1,
  "titleTemplate": "[{severity}] {ruleName}",
  "messageTemplate": "{jobName}: {fieldPath} evaluated to {actualValue}",
  "sections": [
    {
      "id": "cluster-facts",
      "type": "KEY_VALUE",
      "title": "Cluster",
      "fields": [
        { "label": "Cluster", "path": "data.cluster.name" },
        { "label": "Platform status", "path": "response.status" }
      ]
    },
    {
      "id": "consumer-groups",
      "type": "TABLE",
      "title": "Consumer groups",
      "sourcePath": "data.consumerGroups",
      "columns": [
        { "key": "groupId", "label": "Group" },
        { "key": "totalLag", "label": "Total lag" }
      ],
      "maxRows": 20
    },
    {
      "id": "affected-topics",
      "type": "LIST",
      "title": "Affected topics",
      "sourcePath": "data.topics",
      "fields": [
        { "key": "name", "label": "Topic" },
        { "key": "status", "label": "Status" },
        { "key": "totalLag", "label": "Total lag" }
      ],
      "maxItems": 20
    },
    {
      "id": "raw-summary",
      "type": "JSON",
      "title": "Cluster response",
      "sourcePath": "data.cluster",
      "maxLength": 4000
    }
  ]
}
\`\`\`

Presentation section types are TEXT, KEY_VALUE, TABLE, LIST, and JSON. Document source paths must explicitly use response.* or data.*; table column keys and list field keys are relative to each selected record. A LIST source may be an array or a single object (treated as one item). Its configured fields are rendered in order for every item; legacy valuePath remains supported when fields are not configured. This prevents a presentation from silently switching between platform metadata and monitoring payload fields.

AlertPresentationService creates one channel-neutral presentation snapshot for the triggering history event and stores the rendered result in each queued delivery payload. Email HTML/plain text, Teams Adaptive Cards, and WhatsApp text render those same sections with channel-appropriate layouts. Existing alert_subject and alert_message remain the fallback when presentation_config is empty or has no presentation overrides. Title, message, and TEXT templates support the built-in alert placeholders plus direct namespaced JSON placeholders such as {data.cluster.name} and {response.status}.

Migration V54 adds presentation_config JSONB and config_version. The initial configuration contract is version 1. Save-time validation limits section count, field/column count, row/item counts, text sizes, and source path formats. Runtime rendering applies an overall section output limit; malformed optional presentation content falls back to the legacy message rather than blocking notification delivery.
