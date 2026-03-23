#!/bin/bash
# ============================================================
# Kafka 配置脚本 (支持公网访问)
# ============================================================
set -e

KAFKA_HOME="/opt/kafka"
KAFKA_DATA="/data/kafka"

# 获取服务器公网 IP (自动检测)
PUBLIC_IP=$(curl -s http://checkip.amazonaws.com || curl -s http://ifconfig.me || echo "YOUR_PUBLIC_IP")
echo "检测到公网 IP: ${PUBLIC_IP}"

# ---- 配置 Zookeeper ----
echo ">>> 配置 Zookeeper..."
cat > ${KAFKA_HOME}/config/zookeeper.properties << EOF
dataDir=${KAFKA_DATA}/zookeeper
clientPort=2181
maxClientCnxns=0
admin.enableServer=false
EOF

# ---- 配置 Kafka Broker ----
echo ">>> 配置 Kafka Broker..."
cat > ${KAFKA_HOME}/config/server.properties << EOF
# ---- Broker 基础 ----
broker.id=0
num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# ---- 监听地址 (关键: 支持公网访问) ----
# 内网+公网双监听
listeners=PLAINTEXT://0.0.0.0:9092
# 对外暴露公网IP (上位机和控制端通过此地址连接)
advertised.listeners=PLAINTEXT://${PUBLIC_IP}:9092

# ---- 存储 ----
log.dirs=${KAFKA_DATA}/kafka-logs
num.partitions=3
num.recovery.threads.per.data.dir=1

# ---- 数据保留 (保留7天) ----
log.retention.hours=168
log.retention.bytes=-1
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

# ---- 消息大小 (单条最大 5MB) ----
message.max.bytes=5242880
replica.fetch.max.bytes=5242880

# ---- Zookeeper ----
zookeeper.connect=localhost:2181
zookeeper.connection.timeout.ms=18000

# ---- 副本 (单节点设为1) ----
default.replication.factor=1
min.insync.replicas=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
EOF

echo ""
echo "=== Kafka 配置完成 ==="
echo ""
echo "advertised.listeners = PLAINTEXT://${PUBLIC_IP}:9092"
echo ""
echo "重要: 请确保云服务器安全组/防火墙已开放 9092 端口"
echo ""
echo "下一步: 运行 bash start-kafka.sh 启动 Kafka"
