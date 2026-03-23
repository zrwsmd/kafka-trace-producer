#!/bin/bash
# 停止 Kafka
KAFKA_HOME="/opt/kafka"
echo "stopping Kafka Broker..."
${KAFKA_HOME}/bin/kafka-server-stop.sh
sleep 3
echo "stopping Zookeeper..."
${KAFKA_HOME}/bin/zookeeper-server-stop.sh
echo "Kafka stopped"
