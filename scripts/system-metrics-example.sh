#!/usr/bin/env bash
# Dynamic System Metrics example. Requires bash, awk, df, hostname, jq.
# Override thresholds through CPU_WARNING, CPU_CRITICAL, RAM_WARNING, RAM_CRITICAL,
# DISK_WARNING, DISK_CRITICAL, LOAD_WARNING_PER_CORE, and LOAD_CRITICAL_PER_CORE.
set -eu
CPU_WARNING=$(printenv CPU_WARNING || printf '70')
CPU_CRITICAL=$(printenv CPU_CRITICAL || printf '85')
RAM_WARNING=$(printenv RAM_WARNING || printf '75')
RAM_CRITICAL=$(printenv RAM_CRITICAL || printf '90')
DISK_WARNING=$(printenv DISK_WARNING || printf '85')
DISK_CRITICAL=$(printenv DISK_CRITICAL || printf '95')
LOAD_WARNING_PER_CORE=$(printenv LOAD_WARNING_PER_CORE || printf '0.70')
LOAD_CRITICAL_PER_CORE=$(printenv LOAD_CRITICAL_PER_CORE || printf '1.00')
tmp_metrics=$(mktemp)
trap 'rm -f "$tmp_metrics"' EXIT HUP INT TERM
overall_status=HEALTHY

merge_status() {
    case "$1" in
        CRITICAL) overall_status=CRITICAL ;;
        WARNING) [ "$overall_status" = CRITICAL ] || overall_status=WARNING ;;
        UNKNOWN) [ "$overall_status" = CRITICAL ] || [ "$overall_status" = WARNING ] || overall_status=UNKNOWN ;;
        *) : ;;
    esac
}
percent_status() {
    awk -v value="$1" -v warning="$2" -v critical="$3" 'BEGIN {
        if (value >= critical) print "CRITICAL"; else if (value >= warning) print "WARNING"; else print "HEALTHY"
    }'
}
append_metric() {
    jq -cn --arg name "$1" --arg unit "$2" --arg type "$3" --arg status "$4" --arg value "$5" --arg valueType "$6" \
      '{name:$name,value:(if $valueType == "number" then ($value|tonumber) else $value end),unit:$unit,type:$type} +
       (if $status == "" then {} else {status:$status} end)' >> "$tmp_metrics"
}
host=$(hostname 2>/dev/null || printf 'unknown')
cores=$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf '1')
case "$cores" in ''|*[!0-9]*) cores=1 ;; esac
[ "$cores" -gt 0 ] || cores=1

cpu_snapshot() {
    awk '/^cpu / { idle=$5+$6; total=0; for (i=2;i<=NF;i++) total+=$i; printf "%.0f %.0f\n", total, idle; exit }' /proc/stat
}
cpu_before=$(cpu_snapshot || true)
sleep 1
cpu_after=$(cpu_snapshot || true)
if [ -n "$cpu_before" ] && [ -n "$cpu_after" ]; then
    set -- $cpu_before; total_before=$1; idle_before=$2
    set -- $cpu_after; total_after=$1; idle_after=$2
    cpu_pct=$(awk -v tb="$total_before" -v ib="$idle_before" -v ta="$total_after" -v ia="$idle_after" 'BEGIN {
        dt=ta-tb; di=ia-ib; if (dt <= 0) print "0"; else printf "%.2f", 100*(dt-di)/dt
    }')
    cpu_status=$(percent_status "$cpu_pct" "$CPU_WARNING" "$CPU_CRITICAL")
    append_metric "CPU Usage" "%" "percentage" "$cpu_status" "$cpu_pct" number
    merge_status "$cpu_status"
else
    append_metric "CPU Usage" "%" "percentage" UNKNOWN "0" number
    merge_status UNKNOWN
fi

mem_values=$(awk '/^MemTotal:/ {total=$2} /^MemAvailable:/ {available=$2} END {
    if (total>0 && available>=0) printf "%.0f %.0f\n", total, total-available; else exit 1
}' /proc/meminfo 2>/dev/null || true)
if [ -n "$mem_values" ]; then
    set -- $mem_values; mem_total_kb=$1; mem_used_kb=$2
    mem_pct=$(awk -v used="$mem_used_kb" -v total="$mem_total_kb" 'BEGIN {if(total<=0) print "0"; else printf "%.2f",100*used/total}')
    mem_used_mb=$(awk -v v="$mem_used_kb" 'BEGIN {printf "%.0f",v/1024}')
    mem_total_mb=$(awk -v v="$mem_total_kb" 'BEGIN {printf "%.0f",v/1024}')
    mem_status=$(percent_status "$mem_pct" "$RAM_WARNING" "$RAM_CRITICAL")
    append_metric "RAM Usage" "%" "percentage" "$mem_status" "$mem_pct" number
    append_metric "Memory Capacity" "" "text" "" "$mem_used_mb / $mem_total_mb MB used / total" text
    merge_status "$mem_status"
else
    append_metric "RAM Usage" "%" "percentage" UNKNOWN "0" number
    merge_status UNKNOWN
fi

if [ -r /proc/loadavg ]; then
    set -- $(awk '{print $1,$2,$3}' /proc/loadavg); load1=$1; load5=$2; load15=$3
    load_status=$(awk -v load="$load1" -v cores="$cores" -v warning="$LOAD_WARNING_PER_CORE" -v critical="$LOAD_CRITICAL_PER_CORE" 'BEGIN {
        ratio=load/cores; if(ratio>=critical) print "CRITICAL"; else if(ratio>=warning) print "WARNING"; else print "HEALTHY"
    }')
    append_metric "Load Average" "" "text" "$load_status" "$load1 / $load5 / $load15 (1m / 5m / 15m)" text
    merge_status "$load_status"
fi
if [ -r /proc/uptime ]; then
    uptime_seconds=$(awk '{printf "%.0f",$1}' /proc/uptime)
    append_metric "Uptime" "seconds" "duration" "" "$uptime_seconds" number
fi

# Discover local mounted filesystems dynamically; /u01, /u02, etc. are not hardcoded.
if command -v df >/dev/null 2>&1; then
    df -P -l -x tmpfs -x devtmpfs -x squashfs 2>/dev/null | awk 'NR>1 {print $5 "\t" $6}' |
    while IFS="$(printf '\t')" read -r used mount; do
        [ -n "$used" ] && [ -n "$mount" ] || continue
        pct=$(printf '%s' "$used" | tr -d '%')
        case "$pct" in ''|*[!0-9]*) continue ;; esac
        disk_status=$(percent_status "$pct" "$DISK_WARNING" "$DISK_CRITICAL")
        append_metric "$mount" "%" "percentage" "$disk_status" "$pct" number
    done
fi

disk_overall=$(jq -sr '[.[] | .status // empty] | if any(.[]; . == "CRITICAL") then "CRITICAL" elif any(.[]; . == "WARNING") then "WARNING" elif any(.[]; . == "UNKNOWN") then "UNKNOWN" else "HEALTHY" end' "$tmp_metrics")
merge_status "$disk_overall"
jq -n --arg hostname "$host" --arg status "$overall_status" --slurpfile metrics "$tmp_metrics" \
    '{machines:[{hostname:$hostname,status:$status,metrics:$metrics}]}'
