package com.parking.kafka;

import java.io.*;
import java.util.Properties;

/**
 * 配置管理 —— 从 application.properties 或命令行参数加载
 */
public class TraceConfig {

    // -------- Kafka --------
    private String bootstrapServers = "localhost:9092";
    private String topic            = "trace-data";

    // -------- 发送参数 --------
    private int    periodMs         = 1;          // 采集周期 ms
    private int    batchSize        = 100;         // 每包帧数
    private long   maxFrames        = 10_000_000L; // 总帧数 (默认1000万)
    private long   sendIntervalMs   = 50;          // 包间隔 ms
    private String taskId           = "trace_001";

    // -------- Kafka Producer 高级配置 --------
    private String acks             = "all";       // 零丢失核心配置
    private int    retries          = 5;
    private int    batchSizeBytes   = 65536;       // Kafka batch.size (bytes)
    private int    lingerMs         = 10;          // linger.ms
    private int    bufferMemory     = 67108864;    // 64MB buffer
    private boolean enableIdempotence = true;      // 幂等发送

    public TraceConfig() {}

    /**
     * 从 properties 文件加载
     */
    public static TraceConfig load(String path) {
        TraceConfig cfg = new TraceConfig();
        try (InputStream is = new FileInputStream(path)) {
            Properties p = new Properties();
            p.load(is);
            cfg.bootstrapServers = p.getProperty("kafka.bootstrap.servers", cfg.bootstrapServers);
            cfg.topic            = p.getProperty("kafka.topic",             cfg.topic);
            cfg.periodMs         = Integer.parseInt(p.getProperty("trace.period.ms",        String.valueOf(cfg.periodMs)));
            cfg.batchSize        = Integer.parseInt(p.getProperty("trace.batch.size",       String.valueOf(cfg.batchSize)));
            cfg.maxFrames        = Long.parseLong(p.getProperty("trace.max.frames",         String.valueOf(cfg.maxFrames)));
            cfg.sendIntervalMs   = Long.parseLong(p.getProperty("trace.send.interval.ms",   String.valueOf(cfg.sendIntervalMs)));
            cfg.taskId           = p.getProperty("trace.task.id",           cfg.taskId);
            cfg.acks             = p.getProperty("kafka.acks",              cfg.acks);
            cfg.retries          = Integer.parseInt(p.getProperty("kafka.retries",           String.valueOf(cfg.retries)));
            cfg.enableIdempotence= Boolean.parseBoolean(p.getProperty("kafka.enable.idempotence", String.valueOf(cfg.enableIdempotence)));
            cfg.lingerMs         = Integer.parseInt(p.getProperty("kafka.linger.ms",         String.valueOf(cfg.lingerMs)));
        } catch (FileNotFoundException e) {
            System.out.println("[WARN] config file not found: " + path + ", using defaults");
        } catch (IOException e) {
            System.err.println("[ERROR] load config failed: " + e.getMessage());
        }
        return cfg;
    }

    public void dump() {
        System.out.println("=== Trace Config ===");
        System.out.println("  kafka.bootstrap.servers = " + bootstrapServers);
        System.out.println("  kafka.topic             = " + topic);
        System.out.println("  kafka.acks              = " + acks);
        System.out.println("  kafka.idempotence       = " + enableIdempotence);
        System.out.println("  trace.max.frames        = " + maxFrames);
        System.out.println("  trace.batch.size        = " + batchSize);
        System.out.println("  trace.period.ms         = " + periodMs);
        System.out.println("  trace.send.interval.ms  = " + sendIntervalMs);
        long totalBatches = (maxFrames + batchSize - 1) / batchSize;
        double estSeconds = totalBatches * sendIntervalMs / 1000.0;
        System.out.println("  ---- 预估 ----");
        System.out.println("  总包数: " + totalBatches);
        System.out.printf("  预计耗时: %.1f 秒 (%.1f 分钟)%n", estSeconds, estSeconds / 60.0);
        System.out.println("====================");
    }

    // ---- getters ----
    public String getBootstrapServers() { return bootstrapServers; }
    public String getTopic()            { return topic; }
    public int    getPeriodMs()         { return periodMs; }
    public int    getBatchSize()        { return batchSize; }
    public long   getMaxFrames()        { return maxFrames; }
    public long   getSendIntervalMs()   { return sendIntervalMs; }
    public String getTaskId()           { return taskId; }
    public String getAcks()             { return acks; }
    public int    getRetries()          { return retries; }
    public int    getBatchSizeBytes()   { return batchSizeBytes; }
    public int    getLingerMs()         { return lingerMs; }
    public int    getBufferMemory()     { return bufferMemory; }
    public boolean isEnableIdempotence(){ return enableIdempotence; }
}
