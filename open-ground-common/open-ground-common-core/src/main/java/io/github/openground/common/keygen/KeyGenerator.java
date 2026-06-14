package io.github.openground.common.keygen;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主键生成器
 * <p>基于 {@link SequenceProvider} SPI 的缓存型主键生成器，提供三种主键生成方式：</p>
 * <ul>
 *   <li>{@link #getNextKey(String)} — 基于数据库序列生成（格式化后的序号）</li>
 *   <li>{@link #getBusinessKey(String)} — 业务流水号（前缀 + 日期 + 序列）</li>
 *   <li>{@link #getInternalKey()} — 内存雪花算法生成（不依赖数据库）</li>
 *   <li>{@link #getNextKeys(String, int)} — 批量生成</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
public class KeyGenerator {

    private static final int MAX = 50;

    private static final ConcurrentHashMap<String, KeyInfoDomain> CACHE = new ConcurrentHashMap<>(MAX);

    private final SequenceProvider sequenceProvider;

    /**
     * 构造 KeyGenerator
     *
     * @param sequenceProvider 序列提供者 SPI 实现
     */
    public KeyGenerator(SequenceProvider sequenceProvider) {
        this.sequenceProvider = sequenceProvider;
    }

    // ==================== 实例方法（推荐使用） ====================

    /**
     * 获取主键序号（格式化后的序列号）
     *
     * @param keyName 序列名称
     * @return 格式化后的序列号
     */
    public synchronized String nextKey(String keyName) {
        KeyInfoDomain ki = nextKeyinfo(keyName);
        String reg = "%0" + ki.getKeyLen() + "d";
        return String.format(reg, ki.getNextKey());
    }

    /**
     * 获取业务流水号（前缀 + 8位日期 + 序列号）
     *
     * @param keyName 序列名称
     * @return 业务流水号
     */
    public String businessKey(String keyName) {
        KeyInfoDomain ki = nextKeyinfo(keyName);
        String reg = "%0" + ki.getKeyLen() + "d";
        String nextKeyStr = String.format(reg, ki.getNextKey());
        String sysCd = ki.getSysCd() == null ? "" : ki.getSysCd();
        String prefix = ki.getPrefix() == null ? "" : ki.getPrefix();
        return prefix + sysCd + new SimpleDateFormat("yyyyMMdd").format(new Date()) + nextKeyStr;
    }

    /**
     * 批量获取主键
     *
     * @param keyName 序列名称
     * @param count   生成数量
     * @return 主键数组
     */
    public synchronized String[] nextKeys(String keyName, int count) {
        if (count <= 0) {
            return null;
        }
        String[] keys = new String[count];
        KeyInfoDomain firstKey = nextKeyinfo(keyName);
        long maxVal = firstKey.getMaxValue() + 1;
        long nextKeyT = firstKey.getNextKey() + 1;
        String sysCd = firstKey.getSysCd();
        int len = firstKey.getKeyLen();

        keys[0] = formatBusinessKey(firstKey);
        batchNextKeyinfo(keyName, count);

        KeyInfoDomain temp;
        for (int i = 1; i < keys.length; i++) {
            temp = new KeyInfoDomain();
            temp.setSysCd(sysCd);
            temp.setMaxValue(maxVal);
            temp.setNextKey(nextKeyT);
            temp.setKeyLen(len);
            keys[i] = formatBusinessKey(temp);
            nextKeyT++;
            maxVal++;
        }
        return keys;
    }

    /**
     * 获取内存生成的内部主键（不依赖数据库）
     *
     * @return Long 类型主键
     */
    public Long internalKey() {
        return IdGenerator.generateId();
    }

    // ==================== 静态方法（兼容旧版调用方式） ====================

    private static KeyGenerator defaultInstance;

    /**
     * 初始化默认实例（Spring 容器启动后调用）
     */
    public static void init(KeyGenerator instance) {
        defaultInstance = instance;
    }

    /**
     * 获取主键序号
     *
     * @param keyName 序列名称
     * @return 格式化后的序列号
     */
    public static String getNextKey(String keyName) {
        return defaultInstance.nextKey(keyName);
    }

    /**
     * 获取业务流水号
     *
     * @param keyName 序列名称
     * @return 业务流水号
     */
    public static String getBusinessKey(String keyName) {
        return defaultInstance.businessKey(keyName);
    }

    /**
     * 批量获取主键
     *
     * @param keyName 序列名称
     * @param count   生成数量
     * @return 主键数组
     */
    public static String[] getNextKeys(String keyName, int count) {
        return defaultInstance.nextKeys(keyName, count);
    }

    /**
     * 获取内部主键（雪花算法）
     *
     * @return Long 类型主键
     */
    public static Long getInternalKey() {
        return IdGenerator.generateId();
    }

    // ==================== 内部方法 ====================

    private synchronized KeyInfoDomain nextKeyinfo(String keyName) {
        KeyInfoDomain keyinfo = CACHE.get(keyName);
        if (keyinfo == null) {
            if (log.isDebugEnabled()) {
                log.debug("keyinfo 为空，从 SPI 获取: {}", keyName);
            }
            keyinfo = new KeyInfoDomain();
            keyinfo.setPkName(keyName);
            CACHE.put(keyName, keyinfo);
            retrieveFromProvider(keyinfo, 1);
        } else {
            long nextKey = keyinfo.getNextKey();
            if (nextKey >= keyinfo.getMaxValue()) {
                if (log.isDebugEnabled()) {
                    log.debug("nextKey >= maxValue, 重新从 SPI 获取: {}", keyName);
                }
                retrieveFromProvider(keyinfo, 1);
            } else {
                keyinfo.setNextKey(nextKey + 1);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("keyinfo={}", keyinfo);
        }
        return keyinfo;
    }

    private synchronized void batchNextKeyinfo(String keyName, int count) {
        KeyInfoDomain keyinfo = CACHE.get(keyName);
        if (keyinfo == null) {
            keyinfo = new KeyInfoDomain();
            keyinfo.setPkName(keyName);
            CACHE.put(keyName, keyinfo);
            retrieveFromProvider(keyinfo, count);
        } else {
            long nextKey = keyinfo.getNextKey();
            if (nextKey >= keyinfo.getMaxValue()) {
                retrieveFromProvider(keyinfo, count);
            } else {
                keyinfo.setNextKey(nextKey + 1);
            }
        }
    }

    private void retrieveFromProvider(KeyInfoDomain keyinfo, int count) {
        KeyInfoDomain result = sequenceProvider.retrieveAndAdvance(keyinfo.getPkName(), count);
        keyinfo.setMaxValue(result.getMaxValue());
        keyinfo.setStepLen(result.getStepLen());
        keyinfo.setKeyLen(result.getKeyLen());
        keyinfo.setSysCd(result.getSysCd());
        keyinfo.setResetDate(result.getResetDate());
        keyinfo.setResetFreq(result.getResetFreq());
        keyinfo.setRemark(result.getRemark());
        keyinfo.setPrefix(result.getPrefix());
        keyinfo.setNextKey(result.getNextKey());
    }

    private static String formatBusinessKey(KeyInfoDomain ki) {
        String reg = "%0" + ki.getKeyLen() + "d";
        String nextKeyStr = String.format(reg, ki.getNextKey());
        String prefix = ki.getPrefix() == null ? "" : ki.getPrefix();
        return prefix + nextKeyStr;
    }
}
