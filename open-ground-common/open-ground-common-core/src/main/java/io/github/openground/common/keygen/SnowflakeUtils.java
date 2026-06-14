package io.github.openground.common.keygen;

import cn.hutool.core.lang.Singleton;

/**
 * 雪花算法 ID 生成器工具类
 *
 * @author open-ground
 */
public class SnowflakeUtils {

    /**
     * 获取单例的 {@link Snowflake} 对象
     *
     * @return Snowflake 实例
     */
    public static Snowflake getSnowflake() {
        return Singleton.get(Snowflake.class);
    }
}
