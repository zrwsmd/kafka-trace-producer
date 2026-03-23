# Kafka Trace Producer

> 上位机 Trace 数据 Kafka 高吞吐传输方案 —— **1000 万帧零丢失**

## 架构

```
┌──────────┐                 ┌──────────────┐                 ┌──────────┐
│  上位机   │  Kafka Producer │  云服务器     │  Kafka Consumer │  控制端   │
│  ARM64    │ ──────────────→ │  (Kafka)      │ ←────────────── │  前端/IDE │
│  Java     │   TCP/TLS       │  AMD64        │   TCP/TLS       │          │
└──────────┘                 └──────────────┘                 └──────────┘
```

## 快速开始

### 1. 部署 Kafka 服务器 (云服务器 AMD64)

```bash
cd deploy/
bash install-kafka.sh       # 安装 Kafka
bash configure-kafka.sh     # 配置 (自动检测公网IP)
bash start-kafka.sh         # 启动 + 创建 topic
bash test-kafka.sh          # 验证
```

### 2. 配置上位机

编辑 `application.properties`:
```properties
kafka.bootstrap.servers=你的云服务器公网IP:9092
trace.max.frames=10000000
```

### 3. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行 Producer (上位机)
java -jar target/kafka-trace-producer-1.0.0.jar application.properties

# 运行 Consumer (控制端)
java -cp target/kafka-trace-producer-1.0.0.jar com.parking.kafka.SimpleConsumer 你的云服务器IP:9092 trace-data
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `Main.java` | Producer 入口 |
| `TraceConfig.java` | 配置管理 |
| `KafkaTraceProducer.java` | Kafka Producer 封装 (acks=all, 幂等, 零丢失) |
| `TraceSimulator.java` | 数据生成器 (移植自原 MQTT 版, 10个传感器字段) |
| `SimpleConsumer.java` | 控制端 Consumer 示例 |
| `application.properties` | 配置文件 |
| `deploy/*.sh` | Kafka 服务器部署脚本 |

## 零丢失机制

```
Kafka Producer 配置:
  acks=all                → 所有 ISR 副本确认才算成功
  enable.idempotence=true → 幂等发送，网络重试不产生重复
  retries=5               → 自动重试
  max.in.flight=1         → 严格保序
  compression=lz4         → 压缩，减少网络带宽

应用层:
  sendWithRetry()         → 每条消息同步确认 + 应用层重试
  进度实时打印            → 每1%打印一次进度
```

## 性能预估

| send.interval.ms | 速度 | 1000万帧耗时 |
|-------------------|------|-------------|
| 50 (默认) | 20 包/秒, 2000 帧/秒 | ~83 分钟 |
| 10 | 100 包/秒, 10000 帧/秒 | ~17 分钟 |
| 0 (全速) | ~500+ 包/秒 | ~3 分钟 |

> 全速模式取决于网络带宽和 Kafka Broker 性能。
> 建议从默认值开始，逐步调低 interval 观察是否稳定。
