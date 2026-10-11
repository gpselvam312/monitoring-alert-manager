#!/usr/bin/env bash
# Kafka 3.x-compatible consumer and cluster health collector.
# Requires Kafka CLI scripts and Python 3 on the monitoring host.
set -u
export LC_ALL=C

BOOTSTRAP_SERVERS=""
CLUSTER_NAME="kafka-cluster"
KAFKA_BIN=""
WARNING_LAG=""
CRITICAL_LAG=""
EXPECTED_BROKERS=""

usage() {
    cat <<'USAGE'
Usage: kafka-consumer-health.sh --bootstrap-servers host1:9092[,host2:9092]
       [--cluster-name name] [--kafka-bin directory]
       [--warning-lag number] [--critical-lag number] [--expected-brokers number]

Lag thresholds are optional. If neither threshold is configured, consumer lag alone
does not raise a warning or critical status. Connection/CLI failures remain failures.
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --bootstrap-servers|--cluster-name|--kafka-bin|--warning-lag|--critical-lag|--expected-brokers)
            if [ "$#" -lt 2 ]; then
                echo "Missing value for $1" >&2
                exit 2
            fi
            ;;
    esac
    case "$1" in
        --bootstrap-servers) BOOTSTRAP_SERVERS="${2-}"; shift 2 ;;
        --cluster-name) CLUSTER_NAME="${2-}"; shift 2 ;;
        --kafka-bin) KAFKA_BIN="${2-}"; shift 2 ;;
        --warning-lag) WARNING_LAG="${2-}"; shift 2 ;;
        --critical-lag) CRITICAL_LAG="${2-}"; shift 2 ;;
        --expected-brokers) EXPECTED_BROKERS="${2-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [ -z "$BOOTSTRAP_SERVERS" ]; then
    echo "--bootstrap-servers is required." >&2
    usage >&2
    exit 2
fi

is_non_negative_integer() { [[ "$1" =~ ^[0-9]+$ ]]; }
for threshold in "$WARNING_LAG" "$CRITICAL_LAG" "$EXPECTED_BROKERS"; do
    if [ -n "$threshold" ] && ! is_non_negative_integer "$threshold"; then
        echo "Thresholds and expected broker count must be non-negative integers." >&2
        exit 2
    fi
done
if [ -n "$WARNING_LAG" ] && [ -n "$CRITICAL_LAG" ] && [ "$CRITICAL_LAG" -lt "$WARNING_LAG" ]; then
    echo "Critical lag threshold must be greater than or equal to warning threshold." >&2
    exit 2
fi

run_kafka_cli() {
    local command_name="$1"
    shift
    if [ -n "$KAFKA_BIN" ]; then
        "$KAFKA_BIN/$command_name" "$@"
    else
        "$command_name" "$@"
    fi
}

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BROKER_RC=0
TOPIC_RC=0
GROUP_RC=0
VERSION_RC=0

run_kafka_cli kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP_SERVERS" >"$TMP_DIR/brokers.txt" 2>&1 || BROKER_RC=$?
run_kafka_cli kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVERS" --describe >"$TMP_DIR/topics.txt" 2>&1 || TOPIC_RC=$?
run_kafka_cli kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP_SERVERS" --all-groups --describe >"$TMP_DIR/groups.txt" 2>&1 || GROUP_RC=$?
run_kafka_cli kafka-topics.sh --version >"$TMP_DIR/version.txt" 2>&1 || VERSION_RC=$?

python3 - "$TMP_DIR" "$CLUSTER_NAME" "$BOOTSTRAP_SERVERS" "$WARNING_LAG" "$CRITICAL_LAG" "$EXPECTED_BROKERS" "$BROKER_RC" "$TOPIC_RC" "$GROUP_RC" "$VERSION_RC" <<'PY'
import json
import re
import sys
from pathlib import Path
from datetime import datetime

tmp = Path(sys.argv[1])
cluster_name, bootstrap, warning_arg, critical_arg, expected_arg = sys.argv[2:7]
broker_rc, topic_rc, group_rc, version_rc = (int(value) for value in sys.argv[7:11])

def read(name):
    try:
        return (tmp / name).read_text(errors="replace")
    except OSError:
        return ""

def integer(value):
    if value is None or str(value).strip() in ("", "-"):
        return None
    try:
        parsed = int(str(value).strip())
        return parsed if parsed >= 0 else None
    except (ValueError, TypeError):
        return None

warning = integer(warning_arg)
critical = integer(critical_arg)
expected_brokers = integer(expected_arg)
broker_text = read("brokers.txt")
topic_text = read("topics.txt")
group_text = read("groups.txt")
version_text = read("version.txt").strip().splitlines()
kafka_version = version_text[0].strip() if version_text else None

broker_ids = set()
for match in re.finditer(r"\(id:\s*(\d+)", broker_text):
    broker_ids.add(int(match.group(1)))

topics = {}
offline_partitions = 0
for line in topic_text.splitlines():
    if "Topic:" not in line:
        continue
    topic_match = re.search(r"\bTopic:\s*(\S+)", line)
    partition_match = re.search(r"\bPartition:\s*(\d+)", line)
    leader_match = re.search(r"\bLeader:\s*(-?\d+)", line)
    if not topic_match:
        continue
    topic_name = topic_match.group(1)
    topics.setdefault(topic_name, set())
    if partition_match:
        topics[topic_name].add(int(partition_match.group(1)))
        if leader_match and int(leader_match.group(1)) < 0:
            offline_partitions += 1

def split_offset_row(line):
    fields = line.split()
    if len(fields) < 6 or fields[0].upper() in ("GROUP", "CONSUMER", "ERROR"):
        return None
    if not re.fullmatch(r"\d+", fields[2]):
        return None
    return {
        "groupId": fields[0], "topic": fields[1], "partition": int(fields[2]),
        "currentOffset": integer(fields[3]), "logEndOffset": integer(fields[4]),
        "reportedLag": integer(fields[5]),
    }

group_rows = {}
for line in group_text.splitlines():
    parsed = split_offset_row(line)
    if parsed:
        group_rows.setdefault((parsed["groupId"], parsed["topic"]), []).append(parsed)

def lag_status(lag):
    if lag is None:
        return "UNKNOWN"
    if critical is not None and lag > critical:
        return "CRITICAL"
    if warning is not None and lag > warning:
        return "WARNING"
    return "HEALTHY"

group_topics = {}
for (group_id, topic_name), partitions in group_rows.items():
    partitions.sort(key=lambda item: item["partition"])
    for partition in partitions:
        if partition["reportedLag"] is None and partition["currentOffset"] is not None and partition["logEndOffset"] is not None:
            partition["lag"] = max(0, partition["logEndOffset"] - partition["currentOffset"])
        else:
            partition["lag"] = partition["reportedLag"]
        partition["status"] = lag_status(partition["lag"])

    current_values = [p["currentOffset"] for p in partitions]
    end_values = [p["logEndOffset"] for p in partitions]
    lag_values = [p["lag"] for p in partitions]
    current_total = sum(current_values) if all(value is not None for value in current_values) else None
    end_total = sum(end_values) if all(value is not None for value in end_values) else None
    lag_total = sum(lag_values) if all(value is not None for value in lag_values) else None
    known_lags = [value for value in lag_values if value is not None]
    topic = {
        "topic": topic_name,
        "currentOffset": current_total,
        "logEndOffset": end_total,
        "totalLag": lag_total,
        "maxPartitionLag": max(known_lags) if known_lags else None,
        "laggingPartitions": sum(1 for value in known_lags if value > 0),
        "status": lag_status(lag_total),
        "partitions": [
            {"partition": p["partition"], "currentOffset": p["currentOffset"],
             "logEndOffset": p["logEndOffset"], "lag": p["lag"], "status": p["status"]}
            for p in partitions
        ],
    }
    group_topics.setdefault(group_id, []).append(topic)

consumer_groups = []
for group_id, topic_list in group_topics.items():
    topic_list.sort(key=lambda topic: topic["topic"])
    lags = [topic["totalLag"] for topic in topic_list]
    total_lag = sum(lags) if all(value is not None for value in lags) else None
    known_partition_lags = [p["lag"] for topic in topic_list for p in topic["partitions"] if p["lag"] is not None]
    topic_statuses = [topic["status"] for topic in topic_list]
    if total_lag is None:
        status = "UNKNOWN"
    else:
        # Alert thresholds apply to the consumer group's aggregate lag.
        status = lag_status(total_lag)
    consumer_groups.append({
        "groupId": group_id, "totalLag": total_lag,
        "maxPartitionLag": max(known_partition_lags) if known_partition_lags else None,
        "laggingPartitions": sum(1 for value in known_partition_lags if value > 0),
        "status": status, "topics": topic_list,
    })

consumer_groups.sort(key=lambda group: (group["totalLag"] is None, -(group["totalLag"] or 0), group["groupId"]))
group_counts = {
    "healthy": sum(1 for group in consumer_groups if group["status"] == "HEALTHY"),
    "warning": sum(1 for group in consumer_groups if group["status"] == "WARNING"),
    "critical": sum(1 for group in consumer_groups if group["status"] == "CRITICAL"),
    "unknown": sum(1 for group in consumer_groups if group["status"] == "UNKNOWN"),
}

cli_failed = broker_rc != 0 or topic_rc != 0 or group_rc != 0
if cli_failed or not broker_ids:
    cluster_status = "CRITICAL"
elif expected_brokers is not None and len(broker_ids) < expected_brokers:
    cluster_status = "WARNING"
elif offline_partitions > 0:
    cluster_status = "CRITICAL"
else:
    cluster_status = "HEALTHY"

statuses = [cluster_status] + [group["status"] for group in consumer_groups]
if "CRITICAL" in statuses:
    overall_status = "CRITICAL"
elif "WARNING" in statuses:
    overall_status = "WARNING"
elif "UNKNOWN" in statuses:
    overall_status = "UNKNOWN"
else:
    overall_status = "HEALTHY"

message = "Kafka cluster health collected."
if cli_failed:
    message = "One or more Kafka CLI health checks failed; see monitoring execution output."
elif not broker_ids:
    message = "No Kafka brokers could be identified from the broker API response."
elif offline_partitions:
    message = "Kafka has offline partitions."
elif expected_brokers is not None and len(broker_ids) < expected_brokers:
    message = "Fewer Kafka brokers are available than configured as expected."
elif overall_status == "CRITICAL":
    message = "One or more consumer groups exceeded the configured critical lag threshold."
elif overall_status == "WARNING":
    message = "One or more consumer groups exceeded the configured warning lag threshold."
elif overall_status == "UNKNOWN":
    message = "Some consumer offsets are unavailable; lag status is unknown."

result = {
    "schemaVersion": 1, "status": overall_status, "message": message,
    "data": {
        "checkedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "cluster": {
            "name": cluster_name, "kafkaVersion": kafka_version, "bootstrapServers": bootstrap,
            "status": cluster_status, "expectedBrokers": expected_brokers,
            "availableBrokers": len(broker_ids), "brokerIds": sorted(broker_ids),
            "topicCount": len(topics), "offlinePartitions": offline_partitions,
            "cliChecks": {
                "brokerApi": "OK" if broker_rc == 0 else "FAILED",
                "topicDescribe": "OK" if topic_rc == 0 else "FAILED",
                "consumerGroupDescribe": "OK" if group_rc == 0 else "FAILED",
                "version": "OK" if version_rc == 0 else "UNAVAILABLE",
            },
        },
        "thresholds": {"consumerLag": {"warning": warning, "critical": critical}},
        "consumerGroups": {"total": len(consumer_groups), **group_counts, "items": consumer_groups},
    },
}
print(json.dumps(result, separators=(",", ":"), ensure_ascii=False))
sys.exit(1 if cli_failed or not broker_ids else 0)
PY
