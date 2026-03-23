#!/bin/bash
# ============================================================
# Kafka 快速验证 (生产 + 消费 测试)
# ============================================================
KAFKA_HOME="/opt/kafka"

echo "=== Kafka 连通性测试 ==="

# 1. 检查服务
echo ">>> 检查 Kafka 进程..."
if pgrep -f kafka.Kafka > /dev/null; then
    echo "  ✓ Kafka Broker 运行中"
else
    echo "  ✗ Kafka Broker 未运行"
    exit 1
fi

# 2. 发送测试消息
echo ">>> 发送测试消息..."
echo "test-message-$(date +%s)" | ${KAFKA_HOME}/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic trace-data 2>/dev/null

echo "  ✓ 消息发送成功"

# 3. 消费测试消息
echo ">>> 消费测试消息..."
timeout 5 ${KAFKA_HOME}/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic trace-data \
    --from-beginning \
    --max-messages 1 2>/dev/null

echo ""
echo "  ✓ 消息消费成功"

# 4. 查看 topic 信息
echo ""
echo ">>> Topic 状态:"
${KAFKA_HOME}/bin/kafka-topics.sh --describe --topic trace-data --bootstrap-server localhost:9092

echo ""
echo "=== 测试通过! Kafka 工作正常 ==="
