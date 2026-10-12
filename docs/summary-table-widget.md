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
- Eligible numeric columns generate summary cards. summary.excludedFields excludes fields from summary cards and the overall total.
- summary.totalCard.enabled controls the optional overall total card. The overall total is the sum of eligible column totals unless summary.totalCard.field specifies a numeric field.
- summary.totalRow identifies a source total row. That row is used as the authoritative value for each numeric summary card and is not rendered as a machine detail row. If a total row is not supplied, totals are calculated from detail rows.
- Each field in details.columns can be visible or hidden. Hidden fields remain available for grouping, search, sorting, and status rules when configured for those purposes.
- details.search, details.sorting, and details.pagination can each be enabled or disabled independently.
- details.groupBy.fields supports multiple grouping levels, including hidden fields. groupable: false prevents a configured column from being used for grouping.
- details.rowStatus maps source status values to healthy, warning, critical, or neutral row styles. thresholds can define numeric warning/critical boundaries by field.
- Set header.enabled to false to hide the standard widget header; summary cards and the details table remain independent.

The current implementation is client-rendered. It expects structured JSON for reliable field mapping; fixed-width shell output should be converted to JSON by the script or a wrapper.
