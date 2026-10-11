#!/usr/bin/env bash
# Collect Elasticsearch cluster and per-node health for the Monitoring Alert Manager.
# Requires curl and jq. Credentials are supplied via ES_USERNAME/ES_PASSWORD or ES_API_KEY.
set -u
export LC_ALL=C

ES_URL=""
CLUSTER_LABEL=""
WARNING_CPU="${ES_WARNING_CPU:-75}"
CRITICAL_CPU="${ES_CRITICAL_CPU:-90}"
WARNING_JVM="${ES_WARNING_JVM:-75}"
CRITICAL_JVM="${ES_CRITICAL_JVM:-90}"
WARNING_DISK="${ES_WARNING_DISK:-80}"
CRITICAL_DISK="${ES_CRITICAL_DISK:-90}"
TIMEOUT="${ES_TIMEOUT_SECONDS:-10}"

usage() {
    cat <<'USAGE'
Usage: elasticsearch-health.sh --url https://es.example:9200 [--cluster-name label]
  [--warning-cpu 75] [--critical-cpu 90]
  [--warning-jvm 75] [--critical-jvm 90]
  [--warning-disk 80] [--critical-disk 90] [--timeout 10]

Optional authentication:
  ES_USERNAME and ES_PASSWORD for HTTP Basic authentication, or ES_API_KEY.
  ES_CA_CERT may point to a CA certificate for TLS verification.
Thresholds can be overridden using the matching ES_WARNING_*/ES_CRITICAL_* environment variables.
USAGE
}
while [ "$#" -gt 0 ]; do
    case "$1" in
        --url|--cluster-name|--warning-cpu|--critical-cpu|--warning-jvm|--critical-jvm|--warning-disk|--critical-disk|--timeout)
            if [ "$#" -lt 2 ]; then echo "Missing value for $1" >&2; exit 2; fi
            case "$1" in
                --url) ES_URL="$2" ;;
                --cluster-name) CLUSTER_LABEL="$2" ;;
                --warning-cpu) WARNING_CPU="$2" ;;
                --critical-cpu) CRITICAL_CPU="$2" ;;
                --warning-jvm) WARNING_JVM="$2" ;;
                --critical-jvm) CRITICAL_JVM="$2" ;;
                --warning-disk) WARNING_DISK="$2" ;;
                --critical-disk) CRITICAL_DISK="$2" ;;
                --timeout) TIMEOUT="$2" ;;
            esac
            shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done
if [ -z "$ES_URL" ]; then echo "--url is required." >&2; exit 2; fi
for threshold in "$WARNING_CPU" "$CRITICAL_CPU" "$WARNING_JVM" "$CRITICAL_JVM" "$WARNING_DISK" "$CRITICAL_DISK"; do
    if ! [[ "$threshold" =~ ^[0-9]+([.][0-9]+)?$ ]] || ! awk -v n="$threshold" 'BEGIN { exit !(n >= 0 && n <= 100) }'; then
        echo "Thresholds must be numbers from 0 to 100." >&2; exit 2
    fi
done
if ! [[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]]; then echo "Timeout must be a positive integer." >&2; exit 2; fi
if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
    echo "curl and jq are required." >&2; exit 2
fi
ES_URL="${ES_URL%/}"
CURL_ARGS=(-fsS --connect-timeout "$TIMEOUT" --max-time "$TIMEOUT")
if [ -n "${ES_API_KEY:-}" ]; then CURL_ARGS+=(-H "Authorization: ApiKey $ES_API_KEY")
elif [ -n "${ES_USERNAME:-}" ]; then
    if [ -z "${ES_PASSWORD:-}" ]; then echo "ES_PASSWORD is required with ES_USERNAME." >&2; exit 2; fi
    CURL_ARGS+=(-u "$ES_USERNAME:$ES_PASSWORD")
fi
if [ -n "${ES_CA_CERT:-}" ]; then CURL_ARGS+=(--cacert "$ES_CA_CERT"); fi

api() { curl "${CURL_ARGS[@]}" "${ES_URL}$1"; }
fail() {
    local message="$1"
    jq -cn --arg message "$message" --arg label "$CLUSTER_LABEL" \
      '{schemaVersion:1,status:"UNKNOWN",message:$message,data:{version:null,cluster:{name:$label,status:"UNKNOWN"},nodes:[],alerts:[]}}'
    exit 1
}
root_json="$(api /)" || fail "Unable to query Elasticsearch root endpoint; check URL, authentication, TLS and connectivity."
health_json="$(api /_cluster/health)" || fail "Unable to query Elasticsearch cluster health."
stats_json="$(api /_nodes/stats/os,jvm,fs,indices)" || fail "Unable to query Elasticsearch node statistics."
cluster_json="$(api /_cluster/stats)" || fail "Unable to query Elasticsearch cluster statistics."
cat_nodes_json="$(api '/_cat/nodes?format=json&h=id,name,ip,node.role,master')" || fail "Unable to query Elasticsearch node inventory."

jq -en --arg label "$CLUSTER_LABEL" \
  --argjson warnCpu "$WARNING_CPU" --argjson critCpu "$CRITICAL_CPU" \
  --argjson warnJvm "$WARNING_JVM" --argjson critJvm "$CRITICAL_JVM" \
  --argjson warnDisk "$WARNING_DISK" --argjson critDisk "$CRITICAL_DISK" \
  --argjson root "$root_json" --argjson health "$health_json" --argjson stats "$stats_json" \
  --argjson clusterStats "$cluster_json" --argjson catNodes "$cat_nodes_json" '
  def pct($used;$total): if ($used|type) == "number" and ($total|type) == "number" and $total > 0 then (($used * 100 / $total) * 10 | round) / 10 else null end;
  def asnum: if . == null then null else (try tonumber catch null) end;
  [($stats.nodes // {} | to_entries[]) as $entry |
    ($entry.value) as $n |
    ([$catNodes[] | select(.id == $entry.key)] | first // {}) as $cat |
    (pct($n.fs.total.total_in_bytes - $n.fs.total.available_in_bytes; $n.fs.total.total_in_bytes)) as $osDisk |
    ([$n.fs.data[]? | pct(.total_in_bytes - .available_in_bytes; .total_in_bytes)] | map(select(. != null)) | if length == 0 then null else max end) as $dataDisk |
    ($n.os.cpu.percent | asnum) as $cpu |
    ($n.jvm.mem.heap_used_percent | asnum) as $heap |
    ([["CPU",$cpu,$warnCpu,$critCpu],["JVM heap",$heap,$warnJvm,$critJvm],["OS disk",$osDisk,$warnDisk,$critDisk],["Data disk",$dataDisk,$warnDisk,$critDisk]]
      | map(select(.[1] != null and .[1] >= .[2]) | (if .[1] >= .[3] then "CRITICAL: " else "WARNING: " end) + .[0] + " at " + (.[1]|tostring) + "%")) as $alerts |
    {id:$entry.key,name:($cat.name // $n.name // $entry.key),ip:($cat.ip // $n.transport_address // null),
     roles:($n.roles // ($cat["node.role"] // "" | split(","))),master:(($cat.master // "false") == "*"),
     cpuPercent:$cpu,jvmHeapPercent:$heap,osDiskUsedPercent:$osDisk,dataDiskUsedPercent:$dataDisk,
     searchTotal:($n.indices.search.query_total // null),indexingTotal:($n.indices.indexing.index_total // null),
     status:(if all([$cpu,$heap,$osDisk,$dataDisk][]; . == null) then "UNKNOWN" elif ($alerts|length)>0 then (if any($alerts[]; startswith("CRITICAL:")) then "CRITICAL" else "WARNING" end) else "UP" end),
     alerts:$alerts}
  ] as $nodes |
  (if $health.status == "red" then "FAILURE"
   elif $health.status == "yellow" then "WARNING"
   elif any($nodes[]; .status == "CRITICAL") then "FAILURE"
   elif any($nodes[]; .status == "WARNING") then "WARNING"
   elif $health.status == "green" then "SUCCESS" else "UNKNOWN" end) as $overall |
  {schemaVersion:1,status:$overall,
   message:(if $overall == "SUCCESS" then "Elasticsearch cluster is healthy." elif $overall == "WARNING" then "Elasticsearch cluster has warning conditions." elif $overall == "FAILURE" then "Elasticsearch cluster has critical conditions." else "Elasticsearch cluster health is unknown." end),
   data:{version:$root.version.number,
    cluster:{name:(if $label != "" then $label else $health.cluster_name end),status:($health.status|ascii_upcase),
      numberOfNodes:$health.number_of_nodes,numberOfDataNodes:$health.number_of_data_nodes,
      activePrimaryShards:$health.active_primary_shards,activeShards:$health.active_shards,
      relocatingShards:$health.relocating_shards,initializingShards:$health.initializing_shards,
      unassignedShards:$health.unassigned_shards,indices:$clusterStats.indices.count,
      storeSizeBytes:$clusterStats.indices.store.size_in_bytes,docsCount:$clusterStats.indices.docs.count},
    nodes:$nodes,alerts:[$nodes[] | select(.alerts|length>0) | "\(.name): \(.alerts|join(", "))"],
    thresholds:{warning:{cpu:$warnCpu,jvm:$warnJvm,disk:$warnDisk},critical:{cpu:$critCpu,jvm:$critJvm,disk:$critDisk}}}}
' || fail "Unable to parse Elasticsearch API responses."
