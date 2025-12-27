#!/bin/bash

echo "Waiting for Kafka to be ready..."
sleep 30

echo "Creating Kafka topics for Fleet Tracking System..."

# Create vehicle-gps topic (3 partitions for parallelism)
kafka-topics --create \
  --bootstrap-server kafka:9092 \
  --topic vehicle-gps \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=delete \
  --config retention.ms=86400000 \
  --config segment.ms=3600000

echo "✅ Created: vehicle-gps (3 partitions)"

# Create vehicle-alerts topic (ALL alerts go here)
kafka-topics --create \
  --bootstrap-server kafka:9092 \
  --topic vehicle-alerts \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=delete \
  --config retention.ms=43200000 \
  --config segment.ms=1800000

echo "✅ Created: vehicle-alerts (3 partitions)"

# Create DLQ topic
kafka-topics --create \
  --bootstrap-server kafka:9092 \
  --topic vehicle-gps-dlq \
  --partitions 1 \
  --replication-factor 1 \
  --config cleanup.policy=delete \
  --config retention.ms=604800000

echo "✅ Created: vehicle-gps-dlq (DLQ for failed messages)"

# Create vehicle-stats topic (for Kafka Streams aggregations)
kafka-topics --create \
  --bootstrap-server kafka:9092 \
  --topic vehicle-stats \
  --partitions 1 \
  --replication-factor 1 \
  --config cleanup.policy=compact \
  --config retention.ms=-1 \
  --config cleanup.policy=compact,delete \
  --config delete.retention.ms=86400000

echo "✅ Created: vehicle-stats (compacted topic for stats)"

echo ""
echo "🎯 Topics created successfully!"
echo "📊 Current topics:"
kafka-topics --list --bootstrap-server kafka:9092

echo ""
echo "📋 Topic details:"
echo "1. vehicle-gps (3 partitions) - Raw GPS events"
echo "2. vehicle-alerts (3 partitions) - ALL alerts (speeding/idle/geofence)"
echo "3. vehicle-gps-dlq (1 partition) - Dead letter queue"
echo "4. vehicle-stats (1 partition) - 5-minute window aggregations"