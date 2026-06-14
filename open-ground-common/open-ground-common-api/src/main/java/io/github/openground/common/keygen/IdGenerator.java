package io.github.openground.common.keygen;

import java.util.Date;
import java.util.UUID;

/**
 * 53-bit 唯一 ID 生成器（内存生成，不依赖数据库）
 * <p>基于雪花算法思想，生成 53-bit 的 long 型 ID，适配 JavaScript Number 精度。</p>
 * <ul>
 *   <li>40-bit 时间戳（每 10ms 一个 tick，可使用约 347 年）</li>
 *   <li>5-bit 机器节点（0~31）</li>
 *   <li>8-bit 序列号（每 10ms 最多 256 个）</li>
 * </ul>
 *
 * @author open-ground
 */
public class IdGenerator {

    private static IdGenerator instance = new IdGenerator(0);

    /**
     * 初始化默认实例
     *
     * @param machineId 机器节点 ID（0~31）
     * @return 全局单例
     */
    public static IdGenerator initDefaultInstance(int machineId) {
        instance = new IdGenerator(machineId);
        return instance;
    }

    /**
     * 获取全局单例
     *
     * @return IdGenerator 实例
     */
    public static IdGenerator getInstance() {
        return instance;
    }

    /**
     * 生成下一个唯一 ID
     *
     * @return 53-bit long 型 ID
     */
    public static long generateId() {
        return instance.nextId();
    }

    // total bits=53(max 2^53-1：9007199254740992-1)

    private static final long MACHINE_BIT = 5; // max 31
    private static final long SEQUENCE_BIT = 8; // 256/10ms

    /**
     * mask/max value
     */
    private static final long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long TIMESTMP_LEFT = MACHINE_BIT + SEQUENCE_BIT;

    private final long machineId;
    private long sequence = 0L;
    private long lastStmp = -1L;

    private IdGenerator(long machineId) {
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException(
                    "machineId can't be greater than " + MAX_MACHINE_NUM + " or less than 0");
        }
        this.machineId = machineId;
    }

    /**
     * generate new ID
     *
     * @return 唯一 ID
     */
    public synchronized long nextId() {
        long currStmp = getTimestamp();
        if (currStmp < lastStmp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id");
        }

        if (currStmp == lastStmp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currStmp = getNextTimestamp();
            }
        } else {
            sequence = 0L;
        }

        lastStmp = currStmp;

        return currStmp << TIMESTMP_LEFT
                | machineId << MACHINE_LEFT
                | sequence;
    }

    private long getNextTimestamp() {
        long mill = getTimestamp();
        while (mill <= lastStmp) {
            mill = getTimestamp();
        }
        return mill;
    }

    private long getTimestamp() {
        // per 10ms
        return System.currentTimeMillis() / 10;
    }

    /**
     * 解析 ID 中的时间戳
     *
     * @param id 由 generateId() 生成的 ID
     * @return 时间戳对应的日期
     */
    public static Date parseIdTimestamp(long id) {
        return new Date((id >>> TIMESTMP_LEFT) * 10);
    }

    /**
     * 生成 UUID（去横线）
     *
     * @return 32 位 UUID 字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
