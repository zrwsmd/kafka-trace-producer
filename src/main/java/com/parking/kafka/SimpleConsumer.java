package com.parking.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 控制端 Kafka Consumer 示例
 *
 * 用法:
 *   java -cp kafka-trace-producer-1.0.0.jar com.parking.kafka.SimpleConsumer [bootstrap_servers] [topic]
 *
 * 功能:
 *   - 从 trace-data topic 消费所有数据
 *   - 统计消费的包数和帧数
 *   - 支持从头消费 (auto.offset.reset=earliest)
 */
public class SimpleConsumer {

    public static void main(String[] args) {
        String servers = args.length > 0 ? args[0] : "localhost:9092";
        String topic   = args.length > 1 ? args[1] : "trace-data";

        System.out.println("=== Kafka Trace Consumer ===");
        System.out.println("  servers: " + servers);
        System.out.println("  topic:   " + topic);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,          "trace-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 从头消费
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,  "500");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));

        AtomicLong batchCount = new AtomicLong(0);
        long startTime = System.currentTimeMillis();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("\n=== Consumer 统计 ===");
            System.out.println("消费包数: " + batchCount.get());
            System.out.printf("耗时: %.1f 秒%n", elapsed / 1000.0);
            consumer.close();
        }));

        System.out.println("开始消费... (Ctrl+C 退出)\n");

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                long cnt = batchCount.incrementAndGet();
                if (cnt % 1000 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.printf("[Consumer] received %d batches (%.0f batches/s) "
                            + "partition=%d offset=%d%n",
                            cnt, cnt * 1000.0 / Math.max(elapsed, 1),
                            record.partition(), record.offset());
                }
            }
        }
    }
}
