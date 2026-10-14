# Summary Table widget

The Summary Table is a separate dashboard widget type (SUMMARY_TABLE). It uses the existing dashboard widget data source and fieldConfigJson; it does not change the existing Table or Kafka renderer.

## Example field configuration

For a monitoring job that returns rows like {"Machine":"zuccmravp09","0A":2,"CM":4,"UA":5}, configure the widget type as **Summary Table** and use this JSON:

~~~json
{
  "rowPath": "hosts",
  "header": {
    "enabled": true,
    "title": "Connection Counts",
    "description": "Connection counts by machine"
  },
  "summary": {
    "enabled": true,
    "excludedFields": ["Machine", "Environment", "Status"],
    "totalCard": {
      "enabled": true,
      "label": "Overall Total"
    },
    "totalRow": {
      "mode": "AUTHORITATIVE_ROW",
      "identifyBy": "Machine",
      "value": "Total"
    }
  },
  "details": {
    "enabled": true,
    "columns": [
      {"field": "Machine", "label": "Machine", "visible": true, "sortable": true, "groupable": true},
      {"field": "Environment", "label": "Environment", "visible": false, "sortable": true, "groupable": true},
      {"field": "Status", "label": "Status", "visible": false, "sortable": true, "groupable": true},
      {"field": "0A", "label": "0A", "visible": true, "sortable": true, "groupable": false},
      {"field": "CM", "label": "CM", "visible": true, "sortable": true, "groupable": false},
      {"field": "UA", "label": "UA", "visible": true, "sortable": true, "groupable": false}
    ],
    "search": {
      "enabled": true,
      "placeholder": "Search machines...",
      "fields": ["Machine", "Environment", "Status"]
    },
    "sorting": {
      "enabled": true,
      "defaultField": "Machine",
      "direction": "asc"
    },
    "pagination": {
      "enabled": true,
      "pageSize": 10
    },
    "groupBy": {
      "enabled": false,
      "fields": ["Environment", "Status"],
      "initialState": "EXPANDED",
      "order": "VALUE_ASC",
      "showAggregates": true
    },
    "rowStatus": {
      "enabled": true,
      "field": "Status",
      "mappings": {
        "HEALTHY": "SUCCESS",
        "WARNING": "WARNING",
        "CRITICAL": "ERROR",
        "UNKNOWN": "NEUTRAL"
      }
    }
  }
}
~~~

## Configuration behaviour

- rowPath selects the array of detail rows from the monitoring result payload. Omit it when the payload itself is an array or rows are otherwise detected automatically.
- If `summary.cards` is omitted, eligible numeric columns generate summary cards automatically. If `summary.cards` is explicitly configured, that list controls which cards appear. In the visual configurator, select **Summary card** for each desired field.
- `summary.excludedFields` excludes fields from automatic summary cards and the overall total.
- `summary.totalCard.enabled` controls the optional overall total card. The overall total is the sum of eligible column totals unless `summary.totalCard.field` specifies a numeric field.
- Summary cards are filterable by default when they represent a field or a configured filter. Set `filterable: false` to make a card informational only; informational cards have no selected state or click-to-filter behavior. The visual configurator exposes **Filter on click** for field summary cards. Its **Total Rows** card is informational by default.
- A configured card can count or aggregate a filtered subset by setting `filterField`, `filterOperator`, and `filterValue`. Supported operators are `equals`, `not_equals`, `contains`, `gt`, `gte`, `lt`, `lte`, `empty`, and `missing`. Clicking that card applies the same rule to the detail rows.
- summary.totalRow identifies a source total row. That row is used as the authoritative value for each numeric summary card and is not rendered as a machine detail row. If a total row is not supplied, totals are calculated from detail rows.
- Each field in details.columns can be visible or hidden. Hidden fields remain available for grouping, search, sorting, and status rules when configured for those purposes.
- Column `format` supports `number`, `percentage`/`percent`, `datetime`, `duration` (seconds rendered as `HH:MM:SS`), and `status`; other values are displayed as supplied.
- details.search, details.sorting, and details.pagination can each be enabled or disabled independently.
- `details.groupBy.fields` supports multiple grouping levels, including hidden fields. `groupable: false` prevents a configured column from being used for grouping. Group ordering supports `VALUE_ASC`, `VALUE_DESC`, `COUNT_ASC`, and `COUNT_DESC`.
- `details.rowStatus` maps source status values to healthy, warning, critical, or neutral row styles. `thresholds` can define numeric warning/critical boundaries by field.
- `details.conditionalFormatting.rules` can highlight entire rows or individual cells using field comparisons. Rules support equals/not-equals, contains, numeric comparisons, empty, and missing checks; when rules overlap, critical takes precedence over warning, healthy, and informational.
- Set header.enabled to false to hide the standard widget header; summary cards and the details table remain independent.

The current implementation is client-rendered. It expects structured JSON for reliable field mapping; fixed-width shell output should be converted to JSON by the script or a wrapper.

## Response interpretation in Dashboard Configuration

The Summary Table editor includes a guided response-format selector and sample preview. The sample is only used to discover collections/fields and preview column mappings; it is not stored as monitoring history or used as a runtime fallback.

Response parser settings are saved inside the widget's `fieldConfigJson` under `responseParser`, so separate widgets can interpret the same raw output differently:

```json
{
  "renderer": "SUMMARY_TABLE",
  "responseParser": {
    "type": "DELIMITED",
    "delimiter": "|",
    "headerRow": true,
    "trim": true,
    "ignoreBlankLines": true,
    "commentPrefix": "#"
  },
  "rowPath": "rows",
  "details": {
    "columns": [
      {"field": "group", "label": "Consumer Group", "visible": true},
      {"field": "lag", "label": "Lag", "visible": true, "format": "number"}
    ]
  }
}
```

Supported parser types are `AUTO`, `JSON`, `PLAIN_TEXT`, `DELIMITED`, `KEY_VALUE`, and `REGEX`. Delimited parsing supports delimiter/header/trim/comment handling but currently uses a simple delimiter split, not full CSV quoting or multiline semantics. Key-value parsing supports a separator and ignored line prefixes. Regex parsing defines a `fields` array with `key`, `pattern`, optional numeric `group`, `type`, and `required`.

At dashboard runtime, widget-specific parser settings are applied to the stored raw output without changing the persisted execution history. If the raw output is unavailable, the renderer falls back to the normalized result data.
