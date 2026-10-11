# Elasticsearch health dashboard widget

The Elasticsearch widget follows the dashboard's reusable System Metrics styling conventions, uses shared theme variables and Bootstrap Icons, and renders the cluster banner before three responsive node cards.

## Collector setup

Install scripts/elasticsearch-health.sh on the host where the Monitoring Alert Manager executes SCRIPT jobs. The host requires Bash, curl, jq, and network access to an Elasticsearch HTTP(S) endpoint.

Example script path:

~~~text
/opt/monitoring/scripts/elasticsearch-health.sh
~~~

Example command arguments:

~~~text
--url https://elasticsearch.example:9200 --cluster-name retail-es --warning-cpu 75 --critical-cpu 90 --warning-jvm 75 --critical-jvm 90 --warning-disk 80 --critical-disk 90
~~~

Configure credentials in the monitoring application's execution environment, not in command arguments:
- Basic authentication: ES_USERNAME and ES_PASSWORD.
- API key authentication: ES_API_KEY (takes precedence over Basic authentication).
- Private CA certificate: ES_CA_CERT=/path/to/ca.crt.

TLS certificate verification stays enabled. Thresholds can be supplied as command arguments or via ES_WARNING_CPU, ES_CRITICAL_CPU, ES_WARNING_JVM, ES_CRITICAL_JVM, ES_WARNING_DISK, and ES_CRITICAL_DISK environment variables. Values must be percentages from 0 to 100. Disk thresholding uses the greater of OS filesystem usage and Elasticsearch data-path usage; review the initial defaults for each environment.

The collector calls the Elasticsearch root, cluster health, node statistics, cluster statistics, and node inventory APIs. It emits a versioned JSON envelope with status, message, and data. data.cluster contains cluster health and summary counts; data.nodes contains per-node CPU, JVM heap, OS/data disk usage, cumulative search/index counters, status, and threshold details.

The default per-request timeout is four seconds. Configure the SCRIPT job timeout to allow for five API calls plus overhead. If collection fails, the script emits an UNKNOWN payload and exits non-zero so the execution is not marked successful.

## Dashboard configuration

1. Create a SCRIPT monitoring job using this collector.
2. Configure the script path, command arguments, execution timeout, and environment credentials.
3. Add a dashboard widget with type Elasticsearch and data source Monitoring Job, then select the collector job. This dedicated widget type is intentional because it renders a cluster overview and three-column node cards rather than a standard table.
4. Configure refresh/scheduling according to the desired monitoring cadence.

Cluster health is shown as GREEN, YELLOW, RED, or UNKNOWN from the cluster-health API. Missing node metrics are not treated as healthy. The three node cards show UP, WARNING, CRITICAL, or UNKNOWN based on the collected metrics and configured thresholds.

## Reusable TABLE widget

Use the existing `TABLE` widget when the output should be tabular. Search, sorting, and pagination are shared table features and can be controlled in the JSON Field / Column Configuration. For example:

~~~json
{
  "rowPath": "data.nodes",
  "columns": [
    { "field": "name", "label": "Node", "sortable": true, "searchable": true },
    { "field": "cpuPercent", "label": "CPU", "format": "percent", "sortable": true },
    { "field": "status", "label": "Status", "format": "status", "searchable": true }
  ],
  "sorting": { "enabled": true, "defaultField": "cpuPercent", "direction": "desc" },
  "search": { "enabled": true, "placeholder": "Search Elasticsearch nodes..." },
  "pagination": { "enabled": true, "pageSize": 10 }
}
~~~

The `columns` list controls visible columns and their order. Supported common options include `field` (or `key`), `label`, `visible`, `sortable`, `searchable`, and `format`. Search and pagination are enabled by default; set their `enabled` property to `false` to disable them. Sorting can be disabled globally or for an individual column.
