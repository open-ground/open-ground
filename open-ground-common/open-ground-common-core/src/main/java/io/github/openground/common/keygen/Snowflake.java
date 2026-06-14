package io.github.openground.common.keygen;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.io.Serializable;

/**
 * 16位雪花算法 ID 生成器
 * <p>定制版雪花算法，默认起始时间 2020-6-1，可使用至 2055 年。</p>
 * <ul>
 *   <li>3-bit workerId（0~7）</li>
 *   <li>2-bit dataCenterId（0~4）</li>
 *   <li>8-bit 序列号（每毫秒 0~255）</li>
 * </ul>
 *
 * @author open-ground
 */
public class Snowflake implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 默认起始时间，2020-6-1 00:00:00
     */
    public static long DEFAULT_TWEPOCH = 1590940800000L;
    /**
     * 默认回拨时间，2S
     */
    public static long DEFAULT_TIME_OFFSET = 2000L;

    private static final long WORKER_ID_BITS = 3L;
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);

    private static final long DATA_CENTER_ID_BITS = 2L;
    private static final long MAX_DATA_CENTER_ID = -1L ^ (-1L << DATA_CENTER_ID_BITS);

    private static final long SEQUENCE_BITS = 8L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long twepoch;
    private final long workerId;
    private final long dataCenterId;
    private final boolean useSystemClock;
    private final long timeOffset;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 构造，使用自动生成的 workerId 和 dataCenterId
     */
    public Snowflake() {
        this(IdUtil.getWorkerId(IdUtil.getDataCenterId(MAX_DATA_CENTER_ID), MAX_WORKER_ID));
    }

    /**
     * 构造
     *
     * @param workerId 终端ID（0~7）
     */
    public Snowflake(long workerId) {
        this(workerId, IdUtil.getDataCenterId(MAX_DATA_CENTER_ID));
    }

    /**
     * 构造
     *
     * @param workerId     终端ID（0~7）
     * @param dataCenterId 数据中心ID（0~4）
     */
    public Snowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false);
    }

    /**
     * 构造
     *
     * @param workerId         终端ID
     * @param dataCenterId     数据中心ID
     * @param isUseSystemClock 是否使用 {@link SystemClock}
     */
    public Snowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(null, workerId, dataCenterId, isUseSystemClock);
    }

    /**
     * 构造
     *
     * @param epochDate        初始化时间起点（null 表示默认起始日期）
     * @param workerId         工作机器节点ID
     * @param dataCenterId     数据中心ID
     * @param isUseSystemClock 是否使用 SystemClock
     */
    public Snowflake(java.util.Date epochDate, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(epochDate, workerId, dataCenterId, isUseSystemClock, DEFAULT_TIME_OFFSET);
    }

    /**
     * 构造
     *
     * @param epochDate        初始化时间起点（null 表示默认起始日期）
     * @param workerId         工作机器节点ID
     * @param dataCenterId     数据中心ID
     * @param isUseSystemClock 是否使用 SystemClock
     * @param timeOffset       允许时间回拨的毫秒数
     */
    public Snowflake(java.util.Date epochDate, long workerId, long dataCenterId,
                     boolean isUseSystemClock, long timeOffset) {
        if (null != epochDate) {
            this.twepoch = epochDate.getTime();
        } else {
            this.twepoch = DEFAULT_TWEPOCH;
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    StrUtil.format("workerId can't be greater than {} or less than 0", MAX_WORKER_ID));
        }
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException(
                    StrUtil.format("dataCenterId can't be greater than {} or less than 0", MAX_DATA_CENTER_ID));
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.useSystemClock = isUseSystemClock;
        this.timeOffset = timeOffset;
    }

    /**
     * 根据 Snowflake ID 获取机器ID
     *
     * @param id snowflake 算法生成的 ID
     * @return 机器ID
     */
    public long getWorkerId(long id) {
        return id >> WORKER_ID_SHIFT & ~(-1L << WORKER_ID_BITS);
    }

    /**
     * 根据 Snowflake ID 获取数据中心ID
     *
     * @param id snowflake 算法生成的 ID
     * @return 数据中心ID
     */
    public long getDataCenterId(long id) {
        return id >> DATA_CENTER_ID_SHIFT & ~(-1L << DATA_CENTER_ID_BITS);
    }

    /**
     * 根据 Snowflake ID 获取生成时间
     *
     * @param id snowflake 算法生成的 ID
     * @return 生成时间（毫秒）
     */
    public long getGenerateDateTime(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT & ~(-1L << 41L)) + twepoch;
    }

    /**
     * 下一个 ID
     *
     * @return 唯一 ID
     */
    public synchronized long nextId() {
        long timestamp = genTime();
        if (timestamp < this.lastTimestamp) {
            if (this.lastTimestamp - timestamp < timeOffset) {
                timestamp = lastTimestamp;
            } else {
                throw new IllegalStateException(
                        StrUtil.format("Clock moved backwards. Refusing to generate id for {}ms",
                                lastTimestamp - timestamp));
            }
        }

        if (timestamp == this.lastTimestamp) {
            final long seq = (this.sequence + 1) & SEQUENCE_MASK;
            if (seq == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
            this.sequence = seq;
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - twepoch) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 下一个 ID（字符串形式）
     *
     * @return ID 字符串
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = genTime();
        while (timestamp == lastTimestamp) {
            timestamp = genTime();
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    StrUtil.format("Clock moved backwards. Refusing to generate id for {}ms",
                            lastTimestamp - timestamp));
        }
        return timestamp;
    }

    private long genTime() {
        return this.useSystemClock ? SystemClock.now() : System.currentTimeMillis();
    }
}
