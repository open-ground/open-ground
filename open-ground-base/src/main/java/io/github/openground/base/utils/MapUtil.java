package io.github.openground.base.utils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * Map 工具类：安全、优雅地从 Map&lt;String, Object&gt; 中提取各类值
 * <p>
 * <strong>设计哲学</strong>：
 * <ul>
 *   <li>✅ <strong>防御式编程</strong>：三层空安全校验（Map/key/value），所有转换包裹异常处理</li>
 *   <li>✅ <strong>零异常抛出</strong>：任何转换失败均静默返回默认值，避免中断业务流程</li>
 *   <li>✅ <strong>类型智能转换</strong>：支持 Number/String/Boolean 互转，识别多种布尔语义</li>
 *   <li>✅ <strong>性能优化</strong>：单次 Map 查询，避免重复 get 操作</li>
 *   <li>✅ <strong>金融级精度</strong>：BigDecimal 专用方法，规避 double 精度陷阱</li>
 *   <li>✅ <strong>扩展友好</strong>：提供通用转换器，支持自定义逻辑</li>
 * </ul>
 *
 * @author open-ground
 * @version 1.0.0
 * @since 2024-03-05
 */
public final class MapUtil {

    private MapUtil() {
        throw new UnsupportedOperationException("MapUtil is a utility class and cannot be instantiated");
    }

    // ==================== 基础类型：String ====================

    /**
     * 安全获取 String 类型的值
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null，此时返回 defaultValue）
     * @param defaultValue 转换失败或值不存在时的默认值（可为 null）
     * @return 转换后的 String 值；若任何环节失败则返回 defaultValue
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;
        if (val instanceof String) return (String) val;
        try {
            return val.toString();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 基础类型：Integer ====================

    /**
     * 安全获取 int 基本类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 int 值
     * @return 转换成功的 int 值；失败时返回 defaultValue
     */
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Integer boxed = getInteger(map, key, null);
        return boxed != null ? boxed : defaultValue;
    }

    /**
     * 安全获取 Integer 包装类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 Integer 值（可为 null）
     * @return 转换成功的 Integer 对象；失败时返回 defaultValue
     */
    public static Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        return convertNumber(map, key, Number::intValue, Integer::parseInt, defaultValue);
    }

    // ==================== 基础类型：Long ====================

    /**
     * 安全获取 long 基本类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 long 值
     * @return 转换成功的 long 值；失败时返回 defaultValue
     */
    public static long getLong(Map<String, Object> map, String key, long defaultValue) {
        Long boxed = getLongObj(map, key, null);
        return boxed != null ? boxed : defaultValue;
    }

    /**
     * 安全获取 Long 包装类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 Long 值（可为 null）
     * @return 转换成功的 Long 对象；失败时返回 defaultValue
     */
    public static Long getLongObj(Map<String, Object> map, String key, Long defaultValue) {
        return convertNumber(map, key, Number::longValue, Long::parseLong, defaultValue);
    }

    // ==================== 基础类型：Double ====================

    /**
     * 安全获取 double 基本类型值（带默认值）
     * <p>⚠️ 金融场景请使用 {@link #getBigDecimal(Map, String, BigDecimal)}
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 double 值
     * @return 转换成功的 double 值；失败时返回 defaultValue
     */
    public static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Double boxed = getDoubleObj(map, key, null);
        return boxed != null ? boxed : defaultValue;
    }

    /**
     * 安全获取 Double 包装类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 Double 值（可为 null）
     * @return 转换成功的 Double 对象；失败时返回 defaultValue
     */
    public static Double getDoubleObj(Map<String, Object> map, String key, Double defaultValue) {
        return convertNumber(map, key, Number::doubleValue, Double::parseDouble, defaultValue);
    }

    // ==================== 基础类型：Boolean ====================

    /**
     * 安全获取 boolean 基本类型值（带默认值）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 boolean 值
     * @return 转换成功的 boolean 值；失败时返回 defaultValue
     */
    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Boolean boxed = getBooleanObj(map, key, null);
        return boxed != null ? boxed : defaultValue;
    }

    /**
     * 安全获取 Boolean 包装类型值（带默认值）
     * <p>支持的真值表示（忽略大小写+trim）："true", "1", "yes", "y", "on" 及非零数字
     * <p>支持的假值表示："false", "0", "no", "n", "off" 及数字 0
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 Boolean 值（可为 null）
     * @return 转换成功的 Boolean 对象；失败时返回 defaultValue
     */
    public static Boolean getBooleanObj(Map<String, Object> map, String key, Boolean defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;

        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        if (val instanceof String) {
            String s = ((String) val).trim().toLowerCase();
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "n".equals(s) || "off".equals(s)) return false;
        }
        return defaultValue;
    }

    // ==================== 高精度数值：BigDecimal ====================

    /**
     * 安全获取 BigDecimal 值（金融级精度首选）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 BigDecimal 值（可为 null）
     * @return 转换成功的 BigDecimal 对象；失败时返回 defaultValue
     */
    public static BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;

        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        if (val instanceof String) {
            try {
                return new BigDecimal(((String) val).trim());
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== 集合类型 ====================

    /**
     * 安全获取 List 值（泛型安全，不校验元素类型）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 List 值
     * @return 转换成功的 List 对象；失败时返回 defaultValue
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(Map<String, Object> map, String key, List<T> defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof List) return (List<T>) val;
        return defaultValue;
    }

    /**
     * 安全获取 Map 值（泛型安全）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param defaultValue 转换失败或值不存在时的默认 Map 值
     * @return 转换成功的 Map 对象；失败时返回 defaultValue
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMap(Map<String, Object> map, String key, Map<K, V> defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Map) return (Map<K, V>) val;
        return defaultValue;
    }

    // ==================== 通用对象提取 ====================

    /**
     * 安全获取指定类型的对象（强类型提取）
     *
     * @param map          源 Map（允许为 null）
     * @param key          要查询的键（允许为 null）
     * @param clazz        期望的类型 Class 对象
     * @param defaultValue 转换失败或值不存在时的默认值
     * @return 转换成功的对象；失败时返回 defaultValue
     */
    @SuppressWarnings("unchecked")
    public static <T> T getObject(Map<String, Object> map, String key, Class<T> clazz, T defaultValue) {
        if (map == null || key == null || clazz == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;
        try {
            return clazz.isInstance(val) ? clazz.cast(val) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 高级：自定义转换器 ====================

    /**
     * 通用转换方法：通过自定义函数安全转换值
     *
     * @param map          源 Map
     * @param key          要查询的键
     * @param converter    转换函数
     * @param defaultValue 转换失败时的默认值
     * @return 转换成功的结果；任何环节失败均返回 defaultValue
     */
    public static <T> T convert(
            Map<String, Object> map,
            String key,
            Function<Object, T> converter,
            T defaultValue) {
        if (map == null || key == null || converter == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;
        try {
            return converter.apply(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 内部辅助方法 ====================

    private static <T extends Number> T convertNumber(
            Map<String, Object> map,
            String key,
            Function<Number, T> numberConverter,
            Function<String, T> stringConverter,
            T defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;

        if (val instanceof Number) {
            try {
                return numberConverter.apply((Number) val);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        if (val instanceof String) {
            try {
                return stringConverter.apply(((String) val).trim());
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Description: map key转换为小写
     * @param map
     * @return
     */
    public static Map<String, Object> mapKeyLowerCase(Map map) {
        if (Objects.isNull(map)) {
            return null;
        }
        Map<String, Object> o = new HashMap<>();
        Set<String> set = map.keySet();
        for (String s : set) {
            o.put(s.toLowerCase(), map.get(s));
        }
        return o;
    }

    /**
     * Description: map key转换为大写
     * @param map
     * @return
     */
    public static Map<String, Object> mapKeyUpperCase(Map<String, Object> map) {
        if (Objects.isNull(map)) {
            return null;
        }
        Map<String, Object> o = new HashMap<>();
        Set<String> set = map.keySet();
        for (String s : set) {
            o.put(s.toUpperCase(), map.get(s));
        }
        return o;
    }

    public static Map<String, String> mapKeyUpperCaseStringVal(Map<String, String> map) {
        if (Objects.isNull(map)) {
            return null;
        }
        Map<String, String> o = new HashMap<>();
        Set<String> set = map.keySet();
        for (String s : set) {
            o.put(s.toUpperCase(), map.get(s));
        }
        return o;
    }
}
