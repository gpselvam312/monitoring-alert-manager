# Kafka Consumer Health Widget

Use the existing Script monitoring executor. The script requires Kafka CLI tools and Python 3 on the monitoring host; it connects remotely through the configured bootstrap servers.

## Script arguments

Configure the Monitoring Job command arguments, for example:

\`--bootstrap-servers broker1:9092,broker2:9092,broker3:9092 --cluster-name kafka-prod --expected-brokers 3 --warning-lag 1000 --critical-lag 5000\`

Omit \`--warning-lag\` and/or \`--critical-lag\` when that threshold is not configured. If both are omitted, consumer lag alone will not create a warning or critical status. Connectivity failures and unavailable offsets are reported independently. Threshold comparisons use values greater than the configured threshold.

If Kafka CLI scripts are not on the monitoring process PATH, add \`--kafka-bin /path/to/kafka/bin\`.

## Dashboard Widget Configuration

Create a TABLE widget bound to the Kafka health Monitoring Job. Paste the following JSON into **JSON Field / Column Configuration**:

\`\`\`json
{
  "renderer": "KAFKA_CONSUMER_HEALTH",
  "presentation": {
    "type": "expandable-table"
  },
  "order": [
    "consumerGroup",
    "topic",
    "currentOffset",
    "logEndOffset",
    "lag"
  ],
  "labels": {
    "consumerGroup": "Consumer Group",
    "topic": "Topic",
    "currentOffset": "Current Offset",
    "logEndOffset": "Log-End Offset",
    "lag": "Lag",
    "partition": "Partition"
  },
  "formats": {
    "currentOffset": "number",
    "logEndOffset": "number",
    "lag": "number"
  },
  "rowPath": "consumerGroups.items",
  "visibleFields": [
    "consumerGroup",
    "topic",
    "currentOffset",
    "logEndOffset",
    "lag"
  ],
  "clusterPath": "cluster",
  "clusterNameField": "name",
  "clusterStatusField": "status",
  "clusterAvailableBrokersField": "availableBrokers",
  "clusterExpectedBrokersField": "expectedBrokers",
  "clusterVersionField": "kafkaVersion",
  "clusterTopicCountField": "topicCount",
  "clusterOfflinePartitionsField": "offlinePartitions",
  "groupNameField": "groupId",
  "groupStatusField": "status",
  "groupLagField": "totalLag",
  "topicsField": "topics",
  "topicNameField": "topic",
  "topicCurrentField": "currentOffset",
  "topicEndField": "logEndOffset",
  "topicLagField": "totalLag",
  "partitionsField": "partitions",
  "partitionIdField": "partition",
  "partitionCurrentField": "currentOffset",
  "partitionEndField": "logEndOffset",
  "partitionLagField": "lag"
}
\`\`\`

The Kafka renderer supports the shared `order`, `labels`, `formats`, and `visibleFields` conventions for its main table. `presentation.type` must be `expandable-table`. `visibleFields` accepts `consumerGroup`, `topic`, `currentOffset`, `logEndOffset`, and `lag`; the expand action remains available automatically. Labels for `currentOffset`, `logEndOffset`, and `lag` are also reused in partition details. The widget renders cluster stat cards (available brokers, Kafka version, topics, and offline partitions), consumer-group status counts, search, clickable column-level sorting, pagination by consumer group, and expandable topic partition rows. Lag is sorted highest-first by default. Current/End values on a topic row are sums of its partition offsets; expanded rows show each partition's offsets.

CPU, RAM and storage are intentionally not collected by this script; use the existing Metrics widget for the three Kafka hosts.
