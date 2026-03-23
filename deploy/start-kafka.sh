#!/bin/bash
# ============================================================
# 启动 Kafka (Zookeeper + Broker) - 修复版
# ============================================================
set -e

KAFKA_HOME="/opt/kafka"

echo "=== 启动 Kafka ==="

# 1. 启动 Zookeeper
echo ">>> 启动 Zookeeper..."
sudo -u kafka ${KAFKA_HOME}/bin/zookeeper-server-start.sh -daemon ${KAFKA_HOME}/config/zookeeper.properties
sleep 3

# 检查 Zookeeper 是否启动
if pgrep -f zookeeper > /dev/null; then
    echo "  ✓ Zookeeper started (port 2181)"
else
    echo "  ✗ Zookeeper 启动失败"
    exit 1
fi

# 2. 启动 Kafka Broker
echo ">>> 启动 Kafka Broker..."
sudo -u kafka ${KAFKA_HOME}/bin/kafka-server-start.sh -daemon ${KAFKA_HOME}/config/server.properties
sleep 5

# 检查 Kafka 是否启动
if pgrep -f kafka.Kafka > /dev/null; then
    echo "  ✓ Kafka Broker started (port 9092)"
else
    echo "  ✗ Kafka Broker 启动失败"
    echo "  查看日志: tail -f ${KAFKA_HOME}/logs/server.log"
    exit 1
fi

# 3. 等待 Kafka 完全就绪
echo ">>> 等待 Kafka 就绪..."
sleep 3

# 4. 创建 trace-data topic
echo ">>> 创建 topic: trace-data..."
${KAFKA_HOME}/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --topic trace-data \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists \
    --config retention.ms=604800000 \
    --config max.message.bytes=5242880 2>/dev/null || {
    echo "  topic 可能已存在，继续..."
}

echo ""
echo "=== Kafka 启动完成 ==="
echo ""

# 5. 验证
echo ">>> Topic 列表:"
${KAFKA_HOME}/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

echo ""
echo ">>> Topic 详情:"
${KAFKA_HOME}/bin/kafka-topics.sh --describe --topic trace-data --bootstrap-server localhost:9092

echo ""
echo ">>> 进程状态:"
ps aux | grep -E 'zookeeper|kafka\.Kafka' | grep -v grep

echo ""
echo ">>> 端口监听:"
netstat -tuln | grep -E '2181|9092'

echo ""
echo "✓ Kafka 已就绪, 可以开始发送数据了!"
