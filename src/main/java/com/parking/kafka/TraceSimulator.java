package com.parking.kafka;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trace 数据模拟发送器 (Kafka 版)
 *
 * 从原 MQTT 版 TraceSimulator 移植，核心改动：
 *   - 发送通道从 MQTT QoS1 改为 Kafka (acks=all + 幂等)
 *   - 去掉了 MQTT 的 ACK latch 机制，改用 Kafka 同步 send().get()
 *   - 支持 1000 万帧零丢失传输
 *
 * payload 格式保持与原版完全一致：
 * {
 *   "taskId": "trace_001",
 *   "seq": 1,
 *   "period": 1,
 *   "frames": [ { "ts":1, "axis1_position":12.345, ... }, ... ]
 * }
 */
public class TraceSimulator {

    private final TraceConfig config;
    private final KafkaTraceProducer producer;

    // 发送线程
    private Thread sendThread;
    private volatile boolean running = false;

    // 统计
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong retryCount   = new AtomicLong(0);
    private final List<Long> retrySeqs    = Collections.synchronizedList(new ArrayList<>());

    public TraceSimulator(TraceConfig config, KafkaTraceProducer producer) {
        this.config   = config;
        this.producer = producer;
    }

    // ---- 启动 / 停止 ----

    public void start() {
        if (running) {
            System.out.println("[TraceSimulator] already running");
            return;
        }
        successCount.set(0);
        retryCount.set(0);
        retrySeqs.clear();
        running = true;

        long totalBatches = (config.getMaxFrames() + config.getBatchSize() - 1) / config.getBatchSize();
        System.out.println("[TraceSimulator] starting:"
                + " maxFrames=" + config.getMaxFrames()
                + " batchSize=" + config.getBatchSize()
                + " totalBatches=" + totalBatches
                + " sendInterval=" + config.getSendIntervalMs() + "ms"
                + " topic=" + config.getTopic());

        sendThread = new Thread(this::sendLoop, "trace-simulator");
        sendThread.setDaemon(false);
        sendThread.start();
    }

    public void awaitComplete() {
        if (sendThread == null) return;
        try {
            sendThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void startAndWait() {
        start();
        awaitComplete();
    }

    public void stop() {
        running = false;
        if (sendThread != null) {
            sendThread.interrupt();
        }
    }

    // ---- 核心发送循环 ----

    private void sendLoop() {
        long maxFrames   = config.getMaxFrames();
        int  batchSize   = config.getBatchSize();
        int  periodMs    = config.getPeriodMs();
        long totalBatches = (maxFrames + batchSize - 1) / batchSize;

        long tsCounter = 0;
        long frameSent = 0;
        long startTime = System.currentTimeMillis();

        // 进度打印间隔
        long progressInterval = Math.max(totalBatches / 100, 1); // 每1%打印一次

        System.out.println("[TraceSimulator] send loop started: "
                + totalBatches + " batches (" + maxFrames + " frames)");

        for (long seq = 1; seq <= totalBatches && running; seq++) {
            // 构造 payload
            JSONArray frames = new JSONArray();
            int frameCount = 0;
            for (int i = 0; i < batchSize; i++) {
                tsCounter++;
                if (tsCounter > maxFrames) break;

                JSONObject frame = new JSONObject();
                frame.put("ts",             tsCounter);
                frame.put("axis1_position", simulateAxis1Position(tsCounter, periodMs));
                frame.put("axis1_velocity", simulateAxis1Velocity(tsCounter, periodMs));
                frame.put("axis1_torque",   simulateAxis1Torque(tsCounter, periodMs));
                frame.put("axis2_position", simulateAxis2Position(tsCounter, periodMs));
                frame.put("axis2_velocity", simulateAxis2Velocity(tsCounter, periodMs));
                frame.put("motor_rpm",      simulateMotorRpm(tsCounter, periodMs));
                frame.put("motor_temp",     simulateMotorTemp(tsCounter, periodMs));
                frame.put("servo_current",  simulateServoCurrent(tsCounter, periodMs));
                frame.put("servo_voltage",  simulateServoVoltage(tsCounter, periodMs));
                frame.put("pressure_bar",   simulatePressure(tsCounter, periodMs));
                frames.add(frame);
                frameCount++;
            }

            JSONObject payload = new JSONObject();
            payload.put("taskId", config.getTaskId());
            payload.put("seq",    seq);
            payload.put("period", periodMs);
            payload.put("frames", frames);

            String payloadStr = payload.toJSONString();
            frameSent += frameCount;

            // 发送到 Kafka (同步, 带重试)
            try {
                producer.sendWithRetry(config.getTaskId(), payloadStr, 5);
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("[TraceSimulator] FATAL: seq=" + seq
                        + " all retries failed: " + e.getMessage());
                break;
            }

            // 打印进度
            if (seq % progressInterval == 0 || seq == totalBatches) {
                double pct = seq * 100.0 / totalBatches;
                long elapsed = System.currentTimeMillis() - startTime;
                double speed = seq * 1000.0 / Math.max(elapsed, 1);
                long eta = (long)((totalBatches - seq) / Math.max(speed, 0.01));
                System.out.printf("[TraceSimulator] progress: %.1f%% (%d/%d batches, %d frames)"
                        + "  speed=%.0f batches/s  ETA=%ds%n",
                        pct, seq, totalBatches, frameSent, speed, eta);
            }

            // 发送间隔
            if (seq < totalBatches && config.getSendIntervalMs() > 0) {
                try {
                    Thread.sleep(config.getSendIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        running = false;
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("========== TraceSimulator 最终统计 ==========");
        System.out.println("总帧数:   " + frameSent + " / " + maxFrames);
        System.out.println("总包数:   " + successCount.get() + " / " + totalBatches);
        System.out.println("重试次数: " + producer.getRetryCount());
        System.out.printf("总耗时:   %.1f 秒 (%.1f 分钟)%n", elapsed/1000.0, elapsed/60000.0);
        System.out.printf("平均速度: %.0f 包/秒, %.0f 帧/秒%n",
                successCount.get()*1000.0/Math.max(elapsed,1),
                frameSent*1000.0/Math.max(elapsed,1));
        System.out.println("结果:     " + (successCount.get() == totalBatches ? "全部成功 ✓" : "存在失败 ✗"));
        System.out.println("=============================================");
    }

    // ---- 模拟函数 (与原版 TraceSimulator 完全一致) ----

    private double simulateAxis1Position(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(100.0 * Math.sin(2 * Math.PI * 0.5 * t) + noise(0.5));
    }
    private double simulateAxis1Velocity(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(Math.PI * 100.0 * Math.cos(2 * Math.PI * 0.5 * t) + noise(1.0));
    }
    private double simulateAxis1Torque(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(50.0 + 20.0 * Math.sin(2 * Math.PI * 1.0 * t) + noise(0.5));
    }
    private double simulateAxis2Position(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(80.0 * Math.sin(2 * Math.PI * 0.3 * t + 1.0) + noise(0.5));
    }
    private double simulateAxis2Velocity(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(80.0 * 0.3 * 2 * Math.PI * Math.cos(2 * Math.PI * 0.3 * t + 1.0) + noise(0.5));
    }
    private double simulateMotorRpm(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(1500.0 + 300.0 * Math.sin(2 * Math.PI * 0.1 * t) + noise(5.0));
    }
    private double simulateMotorTemp(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(65.0 + 10.0 * Math.sin(2 * Math.PI * 0.02 * t) + noise(0.2));
    }
    private double simulateServoCurrent(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(5.0 + 2.0 * Math.sin(2 * Math.PI * 0.8 * t) + noise(0.1));
    }
    private double simulateServoVoltage(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(48.0 + 0.5 * Math.sin(2 * Math.PI * 0.05 * t) + noise(0.05));
    }
    private double simulatePressure(long ts, int pms) {
        double t = ts * pms / 1000.0;
        return round(5.0 + 1.5 * Math.sin(2 * Math.PI * 0.2 * t) + noise(0.05));
    }

    private double noise(double scale) {
        return (Math.random() - 0.5) * 2 * scale * 0.02;
    }
    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
