package com.parking.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka Producer 封装 —— 零丢失配置
 *
 * 核心保证:
 *   acks=all        → 所有 ISR 副本确认
 *   enable.idempotence=true → 幂等发送，防重复
 *   retries=5       → 自动重试
 *   同步 send().get() → 每条确认后才发下一条
 */
public class KafkaTraceProducer {

    private final KafkaProducer<String, String> producer;
    private final String topic;

    // 统计
    private final AtomicLong sentCount   = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retryCount  = new AtomicLong(0);

    public KafkaTraceProducer(TraceConfig config) {
        this.topic = config.getTopic();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // ==== 零丢失核心配置 ====
        props.put(ProducerConfig.ACKS_CONFIG,              config.getAcks());           // "all"
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.isEnableIdempotence()); // true
        props.put(ProducerConfig.RETRIES_CONFIG,           config.getRetries());         // 5
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);              // 保序

        // ==== 性能优化 ====
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,        config.getBatchSizeBytes());  // 64KB
        props.put(ProducerConfig.LINGER_MS_CONFIG,         config.getLingerMs());        // 10ms
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG,     config.getBufferMemory());    // 64MB
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,  "lz4");                       // 压缩

        // ==== 超时 ====
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,     30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,    120000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,           60000);

        this.producer = new KafkaProducer<>(props);
        System.out.println("[KafkaProducer] initialized: servers=" + config.getBootstrapServers()
                + " topic=" + topic + " acks=" + config.getAcks()
                + " idempotence=" + config.isEnableIdempotence());
    }

    /**
     * 同步发送一条消息，阻塞直到 Kafka Broker 确认。
     * 如果发送失败会抛出异常。
     *
     * @param key   消息 key (用于分区，可传 seq 编号)
     * @param value 消息 value (JSON payload)
     * @return RecordMetadata (包含 offset、partition 等)
     */
    public RecordMetadata sendSync(String key, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        try {
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata meta = future.get(30, TimeUnit.SECONDS);
            sentCount.incrementAndGet();
            return meta;
        } catch (Exception e) {
            failedCount.incrementAndGet();
            throw e;
        }
    }

    /**
     * 带重试的同步发送
     *
     * @param key       消息 key
     * @param value     消息 value
     * @param maxRetry  最大重试次数
     * @return RecordMetadata
     * @throws Exception 所有重试都失败后抛出
     */
    public RecordMetadata sendWithRetry(String key, String value, int maxRetry) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                return sendSync(key, value);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetry) {
                    retryCount.incrementAndGet();
                    System.err.println("[KafkaProducer] send failed (attempt " + attempt
                            + "/" + maxRetry + "): " + e.getMessage() + ", retrying in "
                            + (500 * attempt) + "ms...");
                    Thread.sleep(500L * attempt);
                }
            }
        }
        throw lastException;
    }

    public void flush() {
        producer.flush();
    }

    public void close() {
        producer.flush();
        producer.close();
        System.out.println("[KafkaProducer] closed. sent=" + sentCount.get()
                + " failed=" + failedCount.get() + " retries=" + retryCount.get());
    }

    public long getSentCount()   { return sentCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
    public long getRetryCount()  { return retryCount.get(); }
}
