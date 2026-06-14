package io.github.openground.base.utils;

import io.github.openground.base.exception.CommonException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 日期工具类：提供日期加减、金融日期判断、日期区间等常用功能
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class DateUtils {

    // ==================== 日期加减 ====================

    /**
     * 获得 N（日/月/年）后（前）的时间，输入输出格式一致
     *
     * @param dateStr  日期字符串
     * @param format   日期格式
     * @param timeVlue 时间量（负数为往前）
     * @param timeUnit 时间单位：D-日，M-月，Y-年
     * @return 计算后的日期字符串
     */
    public static String getDateAfter(String dateStr, String format, int timeVlue, String timeUnit) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar now = initCalendar(dateStr, sdf);
            if (timeUnit == null || timeUnit.trim().isEmpty()) {
                throw new CommonException().setMsg("时间单位[" + timeUnit + "]为空");
            }
            timeUnit = timeUnit.trim();
            if ("D".equalsIgnoreCase(timeUnit)) {
                now.add(Calendar.DATE, timeVlue);
            } else if ("M".equalsIgnoreCase(timeUnit)) {
                now.add(Calendar.MONTH, timeVlue);
            } else if ("Y".equalsIgnoreCase(timeUnit)) {
                now.add(Calendar.YEAR, timeVlue);
            }
            return sdf.format(now.getTime());
        } catch (Exception e) {
            log.error("根据参数获取指定时间间隔的日期失败", e);
            throw new CommonException().setMsg("根据参数获取指定时间间隔的日期失败");
        }
    }

    /**
     * 按照给定格式得到当前日期时间
     *
     * @param pattern 日期格式
     * @return 当前日期时间字符串
     */
    public static String getDatetime(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(new Date());
    }

    // ==================== 金融日期判断 ====================

    /**
     * 判断是否是季末
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return true=季末
     */
    @SneakyThrows
    public static boolean isEndOfSeason(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            return calendar.get(Calendar.DAY_OF_MONTH) == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    && (calendar.get(Calendar.MONTH) + 1) % 3 == 0;
        } catch (Exception e) {
            log.error("判断是否是季末失败", e);
            throw new CommonException().setMsg("判断是否是季末失败");
        }
    }

    /**
     * 判断是否是月末
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return true=月末
     */
    @SneakyThrows
    public static boolean isEndOfMonth(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            return calendar.get(Calendar.DATE) == calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        } catch (Exception e) {
            log.error("判断是否是月末失败", e);
            throw new CommonException().setMsg("判断是否是月末失败");
        }
    }

    /**
     * 判断是否是旬末（每月 10 日/20 日/月末）
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return true=旬末
     */
    @SneakyThrows
    public static boolean isTendays(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            if (calendar.get(Calendar.DATE) == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) return true;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            return day == 10 || day == 20;
        } catch (Exception e) {
            log.error("判断是否是旬末失败", e);
            throw new CommonException().setMsg("判断是否是旬末失败");
        }
    }

    /**
     * 判断是否是半年末
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return true=半年末
     */
    @SneakyThrows
    public static boolean isEndOfHalfYear(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            return calendar.get(Calendar.DAY_OF_MONTH) == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    && (calendar.get(Calendar.MONTH) + 1) % 6 == 0;
        } catch (Exception e) {
            log.error("判断是否是半年末失败", e);
            throw new CommonException().setMsg("判断是否是半年末失败");
        }
    }

    /**
     * 判断是否是年末
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return true=年末
     */
    @SneakyThrows
    public static boolean isEndOfYear(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            return calendar.get(Calendar.DAY_OF_MONTH) == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    && (calendar.get(Calendar.MONTH) + 1) % 12 == 0;
        } catch (Exception e) {
            log.error("判断是否是年末失败", e);
            throw new CommonException().setMsg("判断是否是年末失败");
        }
    }

    // ==================== 周期首日获取 ====================

    /**
     * 获取日期所在年的第一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在年第一天
     */
    @SneakyThrows
    public static String getFirstDayOfYear(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            int year = calendar.get(Calendar.YEAR);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在年的第一天失败", e);
            throw new CommonException().setMsg("获取日期所在年的第一天失败");
        }
    }

    /**
     * 获取日期所在月的第一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在月第一天
     */
    @SneakyThrows
    public static String getFirstDayOfMonth(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在月的第一天失败", e);
            throw new CommonException().setMsg("获取日期所在月的第一天失败");
        }
    }

    /**
     * 获取日期所在半年的第一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在半年第一天
     */
    @SneakyThrows
    public static String getFirstDayOfHalfYear(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            int year = calendar.get(Calendar.YEAR);
            int month = (calendar.get(Calendar.MONTH) + 1) >= 7 ? 7 : 1;
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在半年的第一天失败", e);
            throw new CommonException().setMsg("获取日期所在半年的第一天失败");
        }
    }

    /**
     * 获取日期所在季度的第一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在季度第一天
     */
    @SneakyThrows
    public static String getFirstDayOfQuarter(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            int year = calendar.get(Calendar.YEAR);
            int nowMonth = calendar.get(Calendar.MONTH) + 1;
            int month;
            if (nowMonth <= 3) {
                month = 1;
            } else if (nowMonth <= 6) {
                month = 4;
            } else if (nowMonth <= 9) {
                month = 7;
            } else {
                month = 10;
            }
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在季度的第一天失败", e);
            throw new CommonException().setMsg("获取日期所在季度的第一天失败");
        }
    }

    // ==================== 周期末日获取 ====================

    /**
     * 获取日期所在月的最后一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在月最后一天
     */
    @SneakyThrows
    public static String getEndOfMonth(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在月的最后一天失败", e);
            throw new CommonException().setMsg("获取日期所在月的最后一天失败");
        }
    }

    /**
     * 获取日期所在年的最后一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 所在年最后一天
     */
    @SneakyThrows
    public static String getEndOfYear(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Calendar calendar = initCalendar(dateStr, sdf);
            int year = calendar.get(Calendar.YEAR);
            calendar.clear();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, calendar.getActualMaximum(Calendar.MONTH));
            calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
            return sdf.format(calendar.getTime());
        } catch (Exception e) {
            log.error("获取日期所在年的最后一天失败", e);
            throw new CommonException().setMsg("获取日期所在年的最后一天失败");
        }
    }

    /**
     * 获取日期上一年的最后一天
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 上年最后一天
     */
    @SneakyThrows
    public static String getEndOfLastYear(String dateStr, String format) {
        try {
            String lastDay = getEndOfYear(dateStr, format);
            return getDateAfter(lastDay, format, -1, "Y");
        } catch (Exception e) {
            log.error("获取当前日期上一年的最后一天失败", e);
            throw new CommonException().setMsg("获取当前日期上一年的最后一天失败");
        }
    }

    // ==================== 字符串与日期互转 ====================

    /**
     * 字符串转日期
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return 日期对象
     */
    @SneakyThrows
    public static Date stringToDate(String dateStr, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(dateStr);
        } catch (Exception e) {
            log.error("日期[" + dateStr + "]解析失败");
            throw new CommonException().setMsg("字符串转日期失败");
        }
    }

    /**
     * 字符串转 java.sql.Date
     *
     * @param dateStr 日期字符串
     * @param format  日期格式
     * @return sql.Date 对象
     */
    public static java.sql.Date stringToSqlDate(String dateStr, String format) {
        Date date = stringToDate(dateStr, format);
        if (date != null) {
            return new java.sql.Date(date.getTime());
        }
        return null;
    }

    /**
     * 日期转指定格式字符串
     *
     * @param date   日期对象
     * @param format 日期格式
     * @return 日期字符串
     */
    public static String dateToString(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    // ==================== 日期比较 ====================

    /**
     * 比较两个字符串日期（忽略 null）
     *
     * @param startDate  日期字符串 1
     * @param startFmt   日期格式 1
     * @param endDate    日期字符串 2
     * @param endFmt     日期格式 2
     * @return 1: 前者大, -1: 后者大, 0: 相等, null 则返回 0
     */
    public static int compareStrDate(String startDate, String startFmt, String endDate, String endFmt) {
        if (startDate == null || endDate == null) return 0;
        try {
            Date start = stringToDate(startDate, startFmt);
            Date end = stringToDate(endDate, endFmt);
            return start.compareTo(end);
        } catch (Exception e) {
            log.error("比较字符串日期失败", e);
            return 0;
        }
    }

    /**
     * 字符串日期格式转换
     *
     * @param dateStr   原始日期字符串
     * @param oldFormat 原始格式
     * @param newFormat 目标格式
     * @return 转换后的日期字符串
     */
    public static String strDateConvert(String dateStr, String oldFormat, String newFormat) {
        Date date = stringToDate(dateStr, oldFormat);
        if (date != null) {
            return dateToString(date, newFormat);
        }
        return null;
    }

    // ==================== 时间差计算 ====================

    /**
     * 计算两个时间差
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param format    时间格式
     * @param str       返回单位：d-天，h-小时，m-分钟，s-秒
     * @return 时间差值
     */
    public static long dateDiff(String startTime, String endTime, String format, String str) {
        SimpleDateFormat sd = new SimpleDateFormat(format);
        long nd = 1000 * 24L * 60L * 60L;
        long nh = 1000 * 60L * 60L;
        long nm = 1000 * 60L;
        long ns = 1000;
        long day = 0, hour = 0, min = 0, sec = 0;
        try {
            long diff = sd.parse(endTime).getTime() - sd.parse(startTime).getTime();
            day = diff / nd;
            hour = diff % nd / nh + day * 24;
            min = diff % nd % nh / nm + day * 24 * 60;
            sec = diff % nd % nh % nm / ns;
        } catch (ParseException e) {
            log.error("计算时间差异常", e);
        }
        if ("h".equalsIgnoreCase(str)) return hour;
        if ("m".equalsIgnoreCase(str)) return min;
        if ("d".equalsIgnoreCase(str)) return day;
        return sec;
    }

    // ==================== 日期区间生成 ====================

    /**
     * 获取一个时间段的所有日期（按天），格式 yyyyMMdd
     *
     * @param startDate 开始日期 yyyyMMdd
     * @param endDate   结束日期 yyyyMMdd
     * @return 日期列表
     */
    public static List<String> getTwoDaysDay(String startDate, String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        List<String> dateList = new ArrayList<>();
        try {
            Date dateOne = sdf.parse(startDate);
            Date dateTwo = sdf.parse(endDate);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateOne);
            dateList.add(startDate);
            while (dateTwo.after(calendar.getTime())) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                dateList.add(sdf.format(calendar.getTime()));
            }
        } catch (Exception e) {
            log.error("获取时间段日期失败", e);
        }
        return dateList;
    }

    /**
     * 获取一个时间段的所有月份（按月），格式 yyyy年MM月
     *
     * @param startDate 开始日期 yyyyMMdd
     * @param endDate   结束日期 yyyyMMdd
     * @return 月份列表
     */
    public static List<String> getTwoDaysMonth(String startDate, String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
        List<String> dateList = new ArrayList<>();
        String minDate = startDate.substring(0, 6);
        String maxDate = endDate.substring(0, 6);
        try {
            Calendar min = Calendar.getInstance();
            Calendar max = Calendar.getInstance();
            min.setTime(sdf.parse(minDate));
            min.set(min.get(Calendar.YEAR), min.get(Calendar.MONTH), 1);
            max.setTime(sdf.parse(maxDate));
            max.set(max.get(Calendar.YEAR), max.get(Calendar.MONTH), 2);
            Calendar curr = min;
            while (curr.before(max)) {
                String currDate = sdf.format(curr.getTime());
                String currYear = currDate.substring(0, 4);
                String currMonth = currDate.substring(4, 6);
                dateList.add(currYear + "年" + currMonth + "月");
                curr.add(Calendar.MONTH, 1);
            }
        } catch (Exception e) {
            log.error("获取时间段月份失败", e);
        }
        return dateList;
    }

    /**
     * 获取一个时间段的所有季度（按季度），格式 yyyy年第N季度
     *
     * @param startDate 开始日期 yyyyMMdd
     * @param endDate   结束日期 yyyyMMdd
     * @return 季度列表
     */
    public static List<String> getTwoDaysQuarter(String startDate, String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
        String minDate = startDate.substring(0, 6);
        String maxDate = endDate.substring(0, 6);
        List<String> dateList = new ArrayList<>();
        try {
            Calendar min = Calendar.getInstance();
            Calendar max = Calendar.getInstance();
            min.setTime(sdf.parse(minDate));
            min.set(min.get(Calendar.YEAR), min.get(Calendar.MONTH), 1);
            max.setTime(sdf.parse(maxDate));
            max.set(max.get(Calendar.YEAR), max.get(Calendar.MONTH), 2);
            Calendar curr = min;
            while (curr.before(max)) {
                String currDate = sdf.format(curr.getTime());
                String currYear = currDate.substring(0, 4);
                String currMonth = currDate.substring(4, 6);
                String quarter = getQuarter(Integer.parseInt(currMonth)) + "";
                dateList.add(currYear + "年第" + quarter + "季度");
                curr.add(Calendar.MONTH, 3);
            }
        } catch (Exception e) {
            log.error("获取时间段季度失败", e);
        }
        return dateList;
    }

    /**
     * 获取一个时间段的所有年份（按年），格式 yyyy年
     *
     * @param startDate 开始日期 yyyyMMdd
     * @param endDate   结束日期 yyyyMMdd
     * @return 年份列表
     */
    public static List<String> getTwoDaysYear(String startDate, String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        String minDate = startDate.substring(0, 4);
        String maxDate = endDate.substring(0, 4);
        List<String> dateList = new ArrayList<>();
        try {
            Calendar min = Calendar.getInstance();
            Calendar max = Calendar.getInstance();
            min.setTime(sdf.parse(minDate));
            min.set(min.get(Calendar.YEAR), min.get(Calendar.MONTH), 1);
            max.setTime(sdf.parse(maxDate));
            max.set(max.get(Calendar.YEAR), max.get(Calendar.MONTH), 2);
            Calendar curr = min;
            while (curr.before(max)) {
                String currYear = sdf.format(curr.getTime());
                dateList.add(currYear + "年");
                curr.add(Calendar.YEAR, 1);
            }
        } catch (Exception e) {
            log.error("获取时间段年份失败", e);
        }
        return dateList;
    }

    // ==================== 获取当前日期时间字符串 ====================

    /**
     * 获取系统当前日期和时间（yyyy-MM-dd HH:mm:ss.SSS）
     */
    public static String getCurryDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    /**
     * 获取系统当前日期和时间（yyyy-MM-dd HH:mm:ss）
     */
    public static String getCurryTimeString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    /**
     * 获取系统当前日期和时间（yyyyMMddHHmmss）
     */
    public static String getCurryLongDateTime() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    /**
     * 获取系统当前日期（yyyyMMdd）
     */
    public static String getCurryDate() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    /**
     * 获取系统当前时间（HH:mm:ss）
     */
    public static String getCurryTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    /**
     * 获取系统当前时间（HHmmssSSS）
     */
    public static String getCurryLongTime() {
        return new SimpleDateFormat("HHmmssSSS").format(new Date());
    }

    // ==================== 内部方法 ====================

    private static Calendar initCalendar(String dateStr, SimpleDateFormat sdf) {
        try {
            Date date = sdf.parse(dateStr);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar;
        } catch (ParseException e) {
            log.error("字符串日期解析失败", e);
            throw new CommonException().setMsg("字符串日期解析失败");
        }
    }

    private static int getQuarter(int month) {
        if (month <= 3) return 1;
        if (month <= 6) return 2;
        if (month <= 9) return 3;
        return 4;
    }
}
