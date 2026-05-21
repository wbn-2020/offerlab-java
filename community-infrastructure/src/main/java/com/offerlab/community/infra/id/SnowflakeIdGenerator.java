package com.offerlab.community.infra.id;

import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器
 */
@Component
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1609459200000L; // 2021-01-01 00:00:00
    private static final int WORKER_ID_BITS = 5;
    private static final int DATACENTER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    private static final long WORKER_ID_MASK = (1L << WORKER_ID_BITS) - 1;
    private static final long DATACENTER_ID_MASK = (1L << DATACENTER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS + DATACENTER_ID_BITS;
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS;

    private final long workerId;
    private final long datacenterId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this.workerId = 1;
        this.datacenterId = 1;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
