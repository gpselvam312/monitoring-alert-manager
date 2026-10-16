# System Metrics Widget JSON Contract

The System Metrics widget preserves its existing machine cards, health summary cards (Total, Healthy, Warning, Critical, Unknown), client-side status filters, severity ordering, and pagination. The collector script owns threshold evaluation. The widget renders the overall machine status and any per-metric status supplied by the script.

## Preferred successful response: dynamic metrics

Return a JSON object with a `machines` array. Each machine has an overall `status` and a `metrics` array. Metric names are data, not application-defined fields, so any filesystem mount or newly added metric can be included without changing the application.

```json
{
  "machines": [
    {
      "hostname": "app-server-01",
      "status": "CRITICAL",
      "metrics": [
        { "name": "CPU Usage", "value": 88.5, "unit": "%", "type": "percentage", "status": "CRITICAL" },
        { "name": "RAM Usage", "value": 82.0, "unit": "%", "type": "percentage", "status": "WARNING" },
        { "name": "Load Average", "value": "2.40 / 1.80 / 1.20", "unit": "", "type": "text" },
        { "name": "Uptime", "value": 432000, "unit": "seconds", "type": "duration" },
        { "name": "Memory Capacity", "value": "6550 / 8192 MB used / total", "unit": "", "type": "text" },
        { "name": "/u01", "value": 88.0, "unit": "%", "type": "percentage", "status": "WARNING" },
        { "name": "/u02", "value": 45.0, "unit": "%", "type": "percentage", "status": "HEALTHY" }
      ]
    }
  ]
}
```

Required machine fields are `hostname`, `status`, and `metrics`. Each metric should include `name` and `value`; `unit`, `type`, and `status` are optional. Recommended types are `percentage`, `number`, `capacity`, `duration`, and `text`. Metric status values may be `HEALTHY`, `WARNING`, `CRITICAL`, or `UNKNOWN`; common aliases are normalized. Overall status is used for summary counts, filtering, and severity ordering. Metric status colors the individual meter/value. Do not duplicate threshold calculations in the dashboard.

### Representation defaults

- `percentage`: health meter.
- `number` or numeric `capacity`: numeric value.
- `duration`: duration display; value is seconds.
- `text`: text display.

Dashboard Configuration discovers metric names across the selected sample rows. For each discovered metric, you can enable/disable it, change its display label, choose Automatic / Health meter / Numeric value / Text / Duration, and set its order. New metric names remain displayable automatically. Saved settings are retained if a metric is temporarily absent from a sample. Health summary cards are configured separately.

## Linux shell collection

The example collector at `scripts/system-metrics-example.sh` demonstrates generating this JSON with standard Linux sources: `/proc/stat` for CPU, `/proc/meminfo` for memory, `/proc/loadavg` for load, `/proc/uptime` for uptime, `df -P -l` for mounted local filesystems, and `hostname` for the host name. It uses `jq` to emit valid JSON and exposes threshold values as environment variables. Metric names and filesystem mounts are discovered at runtime; the script does not hardcode `/u01` or any other mount point.

## Backward-compatible flat-field response

Existing scripts that return flat fields continue to use the legacy renderer and mappings. For example:

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
    }
  ]
}
```

Legacy responses without a status field retain the existing fallback inference for backward compatibility. If a response supplies a status field but its value is missing or unrecognized, the renderer displays Unknown rather than silently marking the machine healthy.

## Collection-level failure

Do not return an empty `machines` array as if collection succeeded. Return an explicit failure object:

```json
{
  "success": false,
  "error": "Unable to collect system metrics"
}
```

The widget displays a collection-failed message for this response. A successful response with no machine rows displays a separate no-data/unknown message.

Filtering and paging are client-side and do not trigger backend requests. Refresh behavior remains controlled by the dashboard's existing manual/auto-refresh settings.
