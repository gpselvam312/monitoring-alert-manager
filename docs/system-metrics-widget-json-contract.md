# System Metrics Widget JSON Contract

The System Metrics widget keeps its existing machine-card layout, summary counts, client-side status filters, severity ordering, and pagination. The collector script owns threshold evaluation; the browser renders the status supplied in the response.

## Successful response

Return a JSON object with a `machines` array. Use these field names where possible:

```json
{
  "machines": [
    {
      "hostname": "app-server-01",
      "cpuPercent": 88.5,
      "memoryPercent": 82.0,
      "loadAverage": 2.4,
      "cpuCores": 4,
      "uptimeHours": 120,
      "diskU01Percent": 88.0,
      "status": "CRITICAL"
    },
    {
      "hostname": "app-server-02",
      "cpuPercent": 32.0,
      "memoryPercent": 48.0,
      "loadAverage": 0.8,
      "cpuCores": 8,
      "uptimeHours": 360,
      "diskU01Percent": 45.0,
      "status": "HEALTHY"
    }
  ]
}
```

Accepted status values are `HEALTHY`, `WARNING`, `CRITICAL`, and `UNKNOWN`. Common aliases such as `OK`, `GREEN`, `WARN`, `YELLOW`, `FAILED`, and `RED` are normalized by the renderer. Missing or unrecognized status values display as `UNKNOWN` when the response uses a status field.

The shell script or its configuration should calculate thresholds and set each machine's `status`. Do not duplicate threshold rules in the widget configuration. If required measurements cannot be collected, report `UNKNOWN` rather than `HEALTHY`.

## Collection-level failure

Do not return an empty `machines` array as if collection succeeded. Return an explicit failure object:

```json
{
  "success": false,
  "error": "Unable to connect to app-server-01"
}
```

The widget displays a collection-failed message for this response. A successful response with no machine rows displays a separate no-data/unknown message.

## Field mapping and view customization

In Dashboard Configuration, choose **System Metrics**, paste a sample response (or load the latest job output), then select **Discover fields / preview**. The System Metrics mapping panel lets you map the hostname, CPU, memory, load averages, CPU cores, uptime, disk, and optional memory-capacity fields to the response. The health status field can be selected separately. These controls save to the widget's field configuration; Advanced JSON remains available for unusual schemas.

The same panel lets you show or hide the summary cards and individual metric rows, customize metric labels, and choose 6, 12, 24, or 48 machines per page. The defaults preserve the existing card layout and all current metrics. The shell script still owns threshold evaluation; the widget does not recalculate thresholds.

For an unusual schema, the equivalent Advanced JSON configuration looks like this:

```json
{
  "rowPath": "machines",
  "statusField": "health",
  "fields": {
    "hostname": "host",
    "cpu": "cpuUsage",
    "ram": "memoryUsage",
    "load1m": "load",
    "cpuCores": "cores",
    "uptimeHours": "uptime",
    "diskUsedPercent": "diskUsage",
    "status": "health"
  }
}
```

Existing responses without a status field retain the legacy status inference for backward compatibility. Existing System Metrics styling and the client-side filter/sort/pagination behavior are unchanged. Filtering and paging do not trigger backend requests; refresh behavior remains controlled by the dashboard's existing manual/auto-refresh settings.
