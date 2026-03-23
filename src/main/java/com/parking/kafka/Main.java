package com.parking.kafka;

/**
 * 上位机 Kafka Trace Producer 入口
 *
 * 用法:
 *   java -jar kafka-trace-producer-1.0.0.jar [config_file]
 *
 * 默认配置文件: application.properties (当前目录)
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== Kafka Trace Producer (1000万帧零丢失) ===");
        System.out.println();

        // 1. 加载配置
        String configPath = args.length > 0 ? args[0] : "application.properties";
        TraceConfig config = TraceConfig.load(configPath);
        config.dump();

        // 2. 创建 Kafka Producer
        KafkaTraceProducer producer = new KafkaTraceProducer(config);

        // 3. 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nshutting down...");
            producer.close();
        }));

        // 4. 创建并启动 TraceSimulator
        TraceSimulator simulator = new TraceSimulator(config, producer);
        System.out.println();
        System.out.println(">>> 开始发送 " + config.getMaxFrames() + " 帧数据到 Kafka ...");
        System.out.println();

        // 同步发送并等待完成
        simulator.startAndWait();

        // 5. flush & close
        producer.close();
        System.out.println("\n程序退出");
    }
}
