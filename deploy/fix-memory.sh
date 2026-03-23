#!/bin/bash
# ============================================================
# 修复 Kafka 内存不足问题
# 适用于小内存云服务器 (1GB ~ 2GB)
# ============================================================
set -e

KAFKA_HOME="/opt/kafka"

echo "=== 修复 Kafka 内存配置 ==="

# 1. 修改 Kafka 启动脚本，减少内存占用
echo ">>> 修改 Kafka 内存配置..."

# 备份原文件
sudo cp ${KAFKA_HOME}/bin/kafka-server-start.sh ${KAFKA_HOME}/bin/kafka-server-start.sh.bak

# 修改 Kafka JVM 参数（从 1GB 降到 256MB）
sudo sed -i 's/export KAFKA_HEAP_OPTS=.*/export KAFKA_HEAP_OPTS="-Xmx256M -Xms256M"/' \
    ${KAFKA_HOME}/bin/kafka-server-start.sh

# 如果上面的 sed 没生效，直接在文件开头添加
if ! grep -q "KAFKA_HEAP_OPTS.*256M" ${KAFKA_HOME}/bin/kafka-server-start.sh; then
    sudo sed -i '2i export KAFKA_HEAP_OPTS="-Xmx256M -Xms256M"' \
        ${KAFKA_HOME}/bin/kafka-server-start.sh
fi

echo "  ✓ Kafka 内存已调整为 256MB"

# 2. 同样调整 ZooKeeper 内存
echo ">>> 修改 ZooKeeper 内存配置..."
sudo cp ${KAFKA_HOME}/bin/zookeeper-server-start.sh ${KAFKA_HOME}/bin/zookeeper-server-start.sh.bak

sudo sed -i 's/export KAFKA_HEAP_OPTS=.*/export KAFKA_HEAP_OPTS="-Xmx128M -Xms128M"/' \
    ${KAFKA_HOME}/bin/zookeeper-server-start.sh

if ! grep -q "KAFKA_HEAP_OPTS.*128M" ${KAFKA_HOME}/bin/zookeeper-server-start.sh; then
    sudo sed -i '2i export KAFKA_HEAP_OPTS="-Xmx128M -Xms128M"' \
        ${KAFKA_HOME}/bin/zookeeper-server-start.sh
fi

echo "  ✓ ZooKeeper 内存已调整为 128MB"

# 3. 停止现有服务
echo ">>> 停止现有服务..."
${KAFKA_HOME}/bin/kafka-server-stop.sh 2>/dev/null || true
${KAFKA_HOME}/bin/zookeeper-server-stop.sh 2>/dev/null || true
sleep 3

# 4. 重新启动
echo ">>> 重新启动服务..."

# 启动 ZooKeeper
sudo -u kafka ${KAFKA_HOME}/bin/zookeeper-server-start.sh -daemon \
    ${KAFKA_HOME}/config/zookeeper.properties
sleep 3

# 启动 Kafka
sudo -u kafka ${KAFKA_HOME}/bin/kafka-server-start.sh -daemon \
    ${KAFKA_HOME}/config/server.properties
sleep 5

# 5. 验证
echo ">>> 验证服务状态..."
if pgrep -f kafka.Kafka > /dev/null; then
    echo "  ✓ Kafka 启动成功"
else
    echo "  ✗ Kafka 启动失败，查看日志:"
    echo "    tail -50 ${KAFKA_HOME}/logs/server.log"
    exit 1
fi

if pgrep -f zookeeper > /dev/null; then
    echo "  ✓ ZooKeeper 启动成功"
fi

# 6. 创建 topic
echo ">>> 创建 topic..."
${KAFKA_HOME}/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --topic trace-data \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists \
    --config retention.ms=604800000 \
    --config max.message.bytes=5242880 2>/dev/null || true

echo ""
echo "=== 修复完成 ==="
echo ""
echo "内存配置:"
echo "  ZooKeeper: 128MB"
echo "  Kafka:     256MB"
echo "  总计:      ~384MB (可在 1GB 内存服务器运行)"
echo ""
echo "验证:"
${KAFKA_HOME}/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
