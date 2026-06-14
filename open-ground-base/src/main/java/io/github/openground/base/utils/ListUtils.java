package io.github.openground.base.utils;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

/**
 * List 集合工具类：排序、求和等
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class ListUtils {

    /**
     * 对 list 的元素按多个属性名称排序
     * list 元素的属性可以是数字、char、String、java.utils.Date
     *
     * @param list        待排序列表
     * @param isAsc       是否升序
     * @param sortNameArr 属性名称列表
     */
    public static <E> void sort(List<E> list, final boolean isAsc, final String... sortNameArr) {
        Collections.sort(list, new Comparator<E>() {
            @Override
            public int compare(E a, E b) {
                int ret = 0;
                try {
                    for (int i = 0; i < sortNameArr.length; i++) {
                        ret = compareObject(sortNameArr[i], isAsc, a, b);
                        if (0 != ret) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                return ret;
            }
        });
    }

    /**
     * list 的每个属性都指定是升序还是降序
     *
     * @param list        待排序列表
     * @param sortNameArr 属性名称数组
     * @param typeArr     每个属性对应的升降序数组，true 升序，false 降序
     */
    public static <E> void sortSj(List<E> list, final String[] sortNameArr, final boolean[] typeArr) {
        if (sortNameArr.length != typeArr.length) {
            throw new RuntimeException("属性数组元素个数和升降序数组元素个数不相等！");
        }
        Collections.sort(list, new Comparator<E>() {
            public int compare(E a, E b) {
                int ret = 0;
                try {
                    for (int i = 0; i < sortNameArr.length; i++) {
                        ret = compareObject(sortNameArr[i], typeArr[i], a, b);
                        if (0 != ret) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                return ret;
            }
        });
    }

    /**
     * 对 2 个对象按照指定属性名称进行排序比较
     *
     * @param sortName 属性名称
     * @param isAsc    true 升序，false 降序
     * @param a        对象 a
     * @param b        对象 b
     * @return 比较结果
     */
    private static <E> int compareObject(final String sortName, final boolean isAsc, E a, E b) throws Exception {
        int ret;
        Object valueOne = forceGetFieldValue(a, sortName);
        Object valueTwo = forceGetFieldValue(b, sortName);
        String strOne = valueOne.toString();
        String strTwo = valueTwo.toString();
        if (valueOne instanceof Number && valueTwo instanceof Number) {
            int maxlen = Math.max(strOne.length(), strTwo.length());
            strOne = addZero2Str((Number) valueOne, maxlen);
            strTwo = addZero2Str((Number) valueTwo, maxlen);
        } else if (valueOne instanceof Date && valueTwo instanceof Date) {
            long timeOne = ((Date) valueOne).getTime();
            long timeTwo = ((Date) valueTwo).getTime();
            int maxlen = Long.toString(Math.max(timeOne, timeTwo)).length();
            strOne = addZero2Str(timeOne, maxlen);
            strTwo = addZero2Str(timeTwo, maxlen);
        }
        if (isAsc) {
            ret = strOne.compareTo(strTwo);
        } else {
            ret = strTwo.compareTo(strOne);
        }
        return ret;
    }

    /**
     * 给数字对象按照指定长度在左侧补 0
     *
     * @param numObj 数字对象
     * @param length 指定的长度
     * @return 补零后的字符串
     */
    public static String addZero2Str(Number numObj, int length) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        nf.setMaximumIntegerDigits(length);
        nf.setMinimumIntegerDigits(length);
        return nf.format(numObj);
    }

    /**
     * 获取指定对象的指定属性值（去除 private、protected 的限制）
     *
     * @param obj       属性名称所在的对象
     * @param fieldName 属性名称
     * @return 属性值
     */
    public static Object forceGetFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        Object object = null;
        boolean accessible = field.isAccessible();
        if (!accessible) {
            field.setAccessible(true);
            object = field.get(obj);
            field.setAccessible(accessible);
            return object;
        }
        object = field.get(obj);
        return object;
    }

    /**
     * list&lt;Map&lt;String, Object&gt;&gt; 指定除却列求和
     *
     * @param list 数据列表
     * @param it   排除的列名数组
     * @return 求和结果 Map
     */
    public static Map<String, Object> getListSum(List<Map<String, Object>> list, String[] it) {
        Map<String, Object> retMap = new HashMap<>();
        for (String key : list.get(0).keySet()) {
            int res = Arrays.binarySearch(it, key);
            if (res > 0) {
                retMap.put(key, "-");
                continue;
            } else {
                BigDecimal num = new BigDecimal("0");
                for (Map<String, Object> mapOne : list) {
                    num = num.add(new BigDecimal(mapOne.get(key).toString()));
                }
                retMap.put(key, num);
            }
        }
        return retMap;
    }

    /**
     * list&lt;Map&lt;String, Object&gt;&gt; 指定除却列求和平均
     *
     * @param list 数据列表
     * @param it   排除的列名数组
     * @return 求和平均结果 Map
     */
    public static Map<String, Object> getListSumAvl(List<Map<String, Object>> list, String[] it) {
        Map<String, Object> retMap = new HashMap<>();
        BigDecimal cal = new BigDecimal(list.size());
        for (String key : list.get(0).keySet()) {
            int res = Arrays.binarySearch(it, key);
            if (res > 0) {
                retMap.put(key, "-");
                continue;
            } else {
                BigDecimal num = new BigDecimal("0");
                for (Map<String, Object> mapOne : list) {
                    num = num.add(new BigDecimal(mapOne.get(key).toString()));
                }
                retMap.put(key, num.divide(cal, RoundingMode.HALF_UP));
            }
        }
        return retMap;
    }
}
