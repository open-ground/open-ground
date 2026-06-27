package io.github.openground.land.common.util;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("taskUtil")
public class TaskDateUtil implements ApplicationContextAware {
    public final static String dateFormat = "yyyy.MM.dd HH:mm:ss";// "yyyy-MM-dd";

    private static DataSource dataSource;
    private static ApplicationContext context;


    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
    }

    public void init() {
        log.debug("日期工具类初始化");
    }

    /**
     * 获得大额日期的上一工作日(日期:yyyy-MM-dd)
     *
     * @return String
     * @version
     */
    public static String getLastDate() {
        String readDt = "";
        try {
            JdbcTemplate jt = new JdbcTemplate(dataSource);

            Map<?, ?> row = jt.queryForMap("SELECT FORMAT_LAST_DATE FROM PARAM_FPS_FORMAT WHERE FORMAT='HVPS'");

            readDt = row.get("FORMAT_LAST_DATE").toString();
            log.debug("获得系统日期:" + readDt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return readDt;
    }


    /**
     * 根据调度组 获得系统跑批日期(日期:yyyyMMdd)
     *
     * @return String
     * @version 1.0
     */
    public static String getSysEodDate(String cpsGroup) {
        String readDt = "";
        try {
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            // 20201106 update by jack.zhang 返回结果改为list，防止出现两个主机活动时间完全一样的情况下，获取跑日日期异常
            List<Map<String, Object>> list = jt.queryForList("SELECT SYS_EOD_DATE FROM TASK_DISPATCH_ACTIVE_HOST WHERE ACTIVE_TIME = " +
                    "(SELECT MAX(ACTIVE_TIME) FROM TASK_DISPATCH_ACTIVE_HOST WHERE CPS_GROUP = ? ) AND  CPS_GROUP = ? ", cpsGroup, cpsGroup);
            if(list.size() > 0) {
                readDt = list.get(0).get("SYS_EOD_DATE").toString();
            } else {
                log.error("获取跑批日期异常，没有当前调度组的记录");
            }
            log.debug("获得系统跑批日期:" + readDt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return readDt;
    }

    /**
     * <p>Description: 获取系统跑批日期</P>
     *
     * @param
     * @return java.lang.String
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2020/7/14 18:33
     */
    public static String getSysEodDate() {
        String readDt = "";
        try {
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            List<Map<String, Object>> list = jt.queryForList("SELECT SYS_EOD_DATE FROM TASK_DISPATCH_ACTIVE_HOST WHERE ACTIVE_TIME = (SELECT MAX(ACTIVE_TIME) FROM TASK_DISPATCH_ACTIVE_HOST)");
            if(list.size() > 0) {
                readDt = list.get(0).get("SYS_EOD_DATE").toString();
            } else {
                log.error("获取跑批日期异常，没有活动主机记录");
            }
            log.debug("获得系统跑批日期:" + readDt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return readDt;
    }

    /**
     * 获得当前系统时间(时间: HH:mm:ss)
     *
     * @return
     * @version 1.0
     */
    public static String getCurrTime() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(now);
    }


    public static String getMachingCurrentTime() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(now);
    }

    public static String getMachingCurrentDate() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(now);

    }

    public static String getFormatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return date == null ? null : sdf.format(date);
    }

    public static Date getDate(String dateStr) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        if (dateStr == null) {
            return new Date();
        }
        try {
            date = format.parse(dateStr);
        } catch (ParseException e) {
        }
        return date;
    }

    public static Date getShortDate(String dateStr) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Date date = null;
        try {
            date = format.parse(dateStr);
        } catch (ParseException e) {
        }
        return date;
    }


    /**
     * �����ַ�"yyyy.MM.dd HH:mm:ss"ת����Ϊ���� simple date parser
     *
     * @param yyyymmddhhmmss date string
     * @return null if yyyymmddhhmmssΪ�ջ�NULL
     */
    public static Date parseDate(String yyyymmddhhmmss) {
        if (yyyymmddhhmmss == null || yyyymmddhhmmss.length() == 0)
            return null;
        if (yyyymmddhhmmss.indexOf("-") < 0 && yyyymmddhhmmss.indexOf(".") < 0) {
            throw new IllegalArgumentException(
                    "日期格式必须为yyyy\"-\"MM\".\"ddhhmmss");
        }

        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);

        try {
            // ��׼���ڸ�ʽ: yyyy.mm.dd hh:mm:ss
            yyyymmddhhmmss = yyyymmddhhmmss.replace('-', '.');
            if (yyyymmddhhmmss.indexOf(":") < 0)
                yyyymmddhhmmss += " 00:00:00";

            ParsePosition pos = new ParsePosition(0);
            Date tempDat = formatter.parse(yyyymmddhhmmss, pos);
            if (tempDat == null) {
                throw new IllegalArgumentException("日期格式化错误!");
            } else
                return tempDat;
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式化错误!:" + yyyymmddhhmmss
                    + " " + e);
        }
    }

    public static Timestamp nowTime() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * 获取月
     */
    public static String getCurrMonth() {
        GregorianCalendar d = new GregorianCalendar();
        int m = d.get(Calendar.MONTH) + 1;
        if (m < 10) {
            return "0" + m;
        } else {
            return "" + m;
        }
    }

    /**
     * 获取年
     *
     * @return
     */
    public static String getCurrYear() {
        GregorianCalendar d = new GregorianCalendar();
        int y = d.get(Calendar.YEAR) % 100;
        if (y < 10) {
            return "0" + y;
        } else {
            return "" + y;
        }

    }



    public static String getNextDay(String forDate) {
        int year = getYear(forDate);
        int month = getMonth(forDate);
        String newDay = "01";
        if (month == 12) {
            year++;
            month = 1;
        } else {
            month++;
        }
        String newMonth;
        if (String.valueOf(month).length() == 1)
            newMonth = "0" + month;
        else
            newMonth = String.valueOf(month);
        String newYear = String.valueOf(year);
        return newYear + "-" + newMonth + "-" + newDay;
    }

    public static int getYear(String forDate) {
        return Integer.parseInt(forDate.substring(0, 4));
    }

    public static int getMonth(String forDate) {
        return Integer.parseInt(forDate.substring(5, 7));
    }

    public static int getDay(String forDate) {
        return Integer.parseInt(forDate.substring(8, 10));
    }


    public static Calendar toCalendar(String forDate) {
        Calendar cal = Calendar.getInstance();
        int year = getYear(forDate);
        int month = getMonth(forDate);
        int day = getDay(forDate);
        cal.set(year, month - 1, day);
        return cal;
    }

    public static Date toDate(String forDate) {
        return toCalendar(forDate).getTime();
    }


    public static long getInterval(String forBeginDate, String forEndDate) {
        long i = Timestamp.valueOf(forBeginDate + " 00:00:00").getTime();
        long j = Timestamp.valueOf(forEndDate + " 00:00:00").getTime();
        return (j - i) / 0x5265c00L;
    }



    public static Date tomorrow(Date forToday) {
        long start = forToday.getTime() + 0x5265c00L;
        return new Date(start);
    }

    public static Date yesterday(Date forToday) {
        long start = forToday.getTime() - 0x5265c00L;
        return new Date(start);
    }



    public static String getDateString(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        String datestr = formatter.format(date).trim();
        return datestr;
    }



    // ***********start add method by fudandan 2014-01-02 14:40****************

    public static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null)
            return false;
        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null)
            return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return isSameDay(cal1, cal2);
    }

    public static boolean isSameInstant(Date date1, Date date2) {
        if (date1 == null || date2 == null)
            return false;
        return date1.getTime() == date2.getTime();
    }

    public static boolean isSameInstant(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null)
            return false;
        return cal1.getTime().getTime() == cal2.getTime().getTime();
    }

    public static Date addYears(Date date, int amount) {
        return add(date, Calendar.YEAR, amount);
    }

    public static Date addMonths(Date date, int amount) {
        return add(date, Calendar.MONTH, amount);
    }

    public static Date addWeeks(Date date, int amount) {
        return add(date, Calendar.WEEK_OF_YEAR, amount);
    }

    public static Date addDays(Date date, int amount) {
        return add(date, Calendar.DAY_OF_MONTH, amount);
    }

    public static Date addHours(Date date, int amount) {
        return add(date, Calendar.HOUR_OF_DAY, amount);
    }

    public static Date addMinutes(Date date, int amount) {
        return add(date, Calendar.MINUTE, amount);
    }

    public static Date addSeconds(Date date, int amount) {
        return add(date, Calendar.SECOND, amount);
    }

    public static Date addMilliseconds(Date date, int amount) {
        return add(date, Calendar.MILLISECOND, amount);
    }

    public static Date add(Date date, int calendarField, int amount) {
        if (date == null)
            throw new IllegalArgumentException("日期不能为空");

        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(calendarField, amount);
        return c.getTime();
    }

    public static String today() {
        return today("yyyy-MM-dd");
    }

    public static String now() {
        return today("yyyy-MM-dd HH:mm:ss SSS");
    }

    public static String today(String pattern) {
        if (pattern == null)
            throw new IllegalArgumentException("日期格式不能为空");
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        String dt = sdf.format(new Date());
        return dt;
    }

    public static Date parseDate2(String src) {
        return parse(src, "yyyy-MM-dd");
    }

    public static Date parseDatetime(String src) {
        return parse(src, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 最大限度的解析日期:完善
     *
     * @param src
     * @return
     */
    public static Date parse(String src) {
        if (!StringUtils.hasText(src))
            return null;
        try {
            String tmp = StringUtils.delete(src, "-");
            tmp = StringUtils.delete(tmp, ".");
            tmp = StringUtils.delete(tmp, "/");
            tmp = StringUtils.delete(tmp, " ");
            String pattern = "yyyyMMdd";
            if (tmp.length() != 8)
                pattern = "yyyyMd";
            return new SimpleDateFormat(pattern).parse(tmp);
        } catch (ParseException ex) {
        }
        try {
            return parseDatetime(src);
        } catch (Exception ex) {
        }
        throw new IllegalArgumentException("实在猜不出的日期格式,请指定格式掩码");
    }

    public static Date parse(String src, String pattern) {
        if (src == null || "".equals(src))
            return null;
        if (!StringUtils.hasText(pattern))
            return parse(src);
        try {
            return new SimpleDateFormat(pattern).parse(src);
        } catch (ParseException ex) {
            throw new IllegalArgumentException("日期格式转换出错,src=" + src + ",pattern=" + pattern);
        }
    }

    public static String formatDate(Date src) {
        return format(src, "yyyy-MM-dd");
    }

    public static String formatDatetime(Date src) {
        return format(src, "yyyy-MM-dd HH:mm:ss");
    }

    public static String format(Date src, String pattern) {
        if (pattern == null)
            throw new IllegalArgumentException("日期格式不能为空");
        if (src == null)
            return null;
        return new SimpleDateFormat(pattern).format(src);
    }

    public static int getLastDayOfMonth(int y, int m) {
        boolean IsLeapYear = (y % 4 == 0) && (y % 100 != 0) || (y % 400 == 0);
        int days = 0;
        switch (m) {
            case 2:
                if (IsLeapYear) {
                    days = 29;
                } else
                    days = 28;
                break;
            case 4:
            case 6:
            case 9:
            case 11:
                days = 30;
                break;
            default:
                days = 31;
                break;
        }
        return days;
    }

    public static Date lastMonthEnd(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        Date re = getMonthEnd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) - 1);
        return re;
    }

    public static Date lastMonthEnd() {
        return lastMonthEnd(new Date());
    }

    public static Date getMonthEnd(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        // month 从0开始。
        cal.set(Calendar.DAY_OF_MONTH, getLastDayOfMonth(year, month + 1));
        return cal.getTime();
    }

    public static Date toMonthEnd(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        // month 从0开始。
        cal.set(Calendar.DAY_OF_MONTH, getLastDayOfMonth(year, month + 1));
        return cal.getTime();
    }

    /**
     * 中文星期几
     *
     * @return
     */
    public static String getCnWeekDay() {
        return getCnWeekDay(new Date());
    }

    /**
     * 中文星期几
     *
     * @return
     */
    public static String getCnWeekDay(Date date) {
        String[] weekDays = {"日", "一", "二", "三", "四", "五", "六"};
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0)
            w = 0;
        return "星期" + weekDays[w];
    }

    /**
     * 取最大时间（到秒）
     */
    public static Date getMaxDateTime() {
        return parseDatetime("9999-12-31 23:59:59");
    }

    /**
     * 取最大日期
     */
    public static Date getMaxDate() {
        return parseDate2("9999-12-31");
    }


    /**
     * 校验日期格式
     *
     * @param date    目标日期字符
     * @param regular 正则表达式
     * @return boolean
     * @Author open-ground
     * @version 1.0
     * @since 2014-2-11 下午4:58:28
     */
    public static boolean checkDateFormat(String date, String regular) {
        // 正则表达式
        Pattern patternDate = Pattern.compile(regular);
        Matcher matcher = patternDate.matcher(date);
        if (!matcher.matches()) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * string 转换成date
     *
     * @param date
     * @param format
     * @return
     */
    public static Date stringToDate(String date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        Date dateResult = null;

        try {
            dateResult = sdf.parse(date);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return dateResult;
    }

    /**
     * 8位时间进行10位转换
     *
     * @param data
     * @return
     */
    public static Date stringToDate(String data) {
        StringBuffer str = new StringBuffer();
        if (data.length() == 8) {
            str.append(data.substring(0, 4));
            str.append("-");
            str.append(data.substring(4, 6));
            str.append("-");
            str.append(data.substring(6, 8));
            return stringToDate(str.toString(), "yyyy-mm-dd");
        } else if (data.length() == 10) {
            return stringToDate(data, "yyyy-mm-dd");
        }
        return null;
    }

    /**
     * 获取年月的最后一天,返回天
     *
     * @param year
     * @param month
     * @return
     * @Author open-ground
     * @version 1.0
     * @since 2015-1-17 下午5:54:59
     */
    public static int getLastDay(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        return lastDay;
    }

    /**
     * 获取年月的最后一天,返回日期
     *
     * @param year
     * @param month
     * @return
     * @Author open-ground
     * @version 1.0
     * @since 2015-1-17 下午5:54:59
     */
    public static String getLastDay(int year, int month, String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));

        return new SimpleDateFormat(format).format(calendar.getTime());
    }

    /**
     * 根据当前时间和周期，计算下次或上次开始时间
     *
     * @param startDate  开始日期，格式为:yyyy-MM-dd HH:mm:ss
     * @param cyclePriod 重复间隔,重复间隔>0时，计算的结果为下次开始时间；重复间隔<0时，计算的结果为上次开始时间
     * @param priodType  周期类型，y=年,M=月,d=日,w=周,h=时,m=分,s=秒
     * @author zhangpengfei
     * @version 1.0
     * @since 2016-02-17
     */
    public static String nextOrBeforPriodTime(String startDate, int cyclePriod, String priodType) {

        String returnValue = "";
        Date date = TaskDateUtil.parseDate(startDate);
        if (null == date) {
            return returnValue;
        }

        Date returnDate = null;
        if ("y".equals(priodType)) { // 年
            returnDate = TaskDateUtil.addYears(date, cyclePriod);
        } else if ("MM".equals(priodType)) { // 月
            returnDate = TaskDateUtil.addMonths(date, cyclePriod);
        } else if ("d".equals(priodType)) { // 日
            returnDate = TaskDateUtil.addDays(date, cyclePriod);
        } else if ("w".equals(priodType)) { // 周
            returnDate = TaskDateUtil.addWeeks(date, cyclePriod);
        } else if ("h".equals(priodType)) { // 时
            returnDate = TaskDateUtil.addHours(date, cyclePriod);
        } else if ("m".equals(priodType)) { // 分
            returnDate = TaskDateUtil.addMinutes(date, cyclePriod);
        } else if ("s".equals(priodType)) { // 秒
            returnDate = TaskDateUtil.addSeconds(date, cyclePriod);
        }

        returnValue = TaskDateUtil.getDateString(returnDate);
        return returnValue;
    }

    /**
     * 计算下次开始执行时间，不会追加计算历史未执行计划时间
     *
     * @param startDate 开始日期，格式为:yyyy-MM-dd HH:mm:ss
     * @param priodType 周期类型，y=年,M=月,d=日,w=周,h=时,m=分,s=秒
     * @return
     * @author jack.zhang
     * @date 2017年3月14日 下午5:38:01
     * @version v_1.0
     */
    public static String nextPriodTimeNoAddition(String startDate, String priodType) {
        log.info("原执行时间:" + startDate + ", 周期类型:" + priodType);
        Calendar c = Calendar.getInstance();
        Calendar origDate = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        String returnValue = "";
        Date date = TaskDateUtil.parseDate(startDate);
        c.setTime(date);
        origDate.setTime(date);
        if (null == date) {
            return returnValue;
        }
        if (origDate.after(now)) { //如果比当前日期大，则直接返回
            return startDate;
        }

        if ("y".equals(priodType)) { // 年
            setYear(c, now);
        } else if ("MM".equals(priodType)) { // 月
            setMonth(c, now, origDate);
        } else if ("d".equals(priodType)) { // 日
            setDay(c, now, origDate);
        } else if ("w".equals(priodType)) { // 周
            setWeek(c, now, origDate);
        } else if ("h".equals(priodType)) { // 时
            setHour(c, now, origDate);
        } else if ("m".equals(priodType)) { // 分
            setMinute(c, now, origDate);
        } else if ("s".equals(priodType)) { // 秒
            setSecond(c, now, origDate);
        }
        String newTime = TaskDateUtil.getDateString(c.getTime());
        log.info("获得新的执行时间:" + newTime);
        return newTime;
    }

    protected static void setYear(Calendar c, Calendar now) {
        if (now.get(Calendar.YEAR) > c.get(Calendar.YEAR)) {
            c.set(Calendar.YEAR, now.get(Calendar.YEAR));
        }
    }

    /**
     * 设置月，若为月频度，如果过了计划日期，则直接设置当前月
     *
     * @param c
     * @param now
     * @param origDate
     * @description:
     * @author open-ground
     * @date 2018年7月4日 下午9:02:17
     */
    protected static void setMonth(Calendar c, Calendar now, Calendar origDate) {
        setYear(c, now);
        if (now.after(origDate)) {
            c.set(Calendar.MONTH, now.get(Calendar.MONTH));
        }
    }

    protected static void setWeek(Calendar c, Calendar now, Calendar origDate) {
        setMonth(c, now, origDate);
        if (now.get(Calendar.WEEK_OF_YEAR) > c.get(Calendar.WEEK_OF_YEAR)) {
            c.set(Calendar.WEEK_OF_YEAR, now.get(Calendar.WEEK_OF_YEAR));
        }
    }

    /**
     * 设置日，若为日频度，如果过了计划日期，则直接设置当前日
     *
     * @param c
     * @param now
     * @description:
     * @author open-ground
     * @date 2018年7月4日 下午8:15:39
     */
    protected static void setDay(Calendar c, Calendar now, Calendar origDate) {
        setMonth(c, now, origDate);
        if (now.after(origDate)) {
            c.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
        }
    }


    protected static void setHour(Calendar c, Calendar now, Calendar origDate) {
        setDay(c, now, origDate);
        c.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
    }

    protected static void setMinute(Calendar c, Calendar now, Calendar origDate) {
        setHour(c, now, origDate);
        c.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
    }

    protected static void setSecond(Calendar c, Calendar now, Calendar origDate) {
        setMinute(c, now, origDate);
        if (now.get(Calendar.SECOND) > c.get(Calendar.SECOND)) {
            c.set(Calendar.SECOND, now.get(Calendar.SECOND));
        }
    }
    /**
     * 将日期字符串转化为SQL DATE对象
     */
    public static java.sql.Date strToSqlDate(String dateStr , String dateFormat){
        Date date = strToDate(dateStr,dateFormat);
        if(date != null){
            return new java.sql.Date(date.getTime());
        }
        return null;

    }
    /**
     * 将日期字符串转化为日期对象
     * @param dateStr 需要转化的日期字符串
     * @param dateFormat 指定的日期格式
     * @return 日期对象
     */
    public static Date strToDate(String dateStr , String dateFormat){
        try {
            if (ObjectUtil.isNull(dateStr)) {
                return null;
            }
            return getSDFormat(dateFormat).parse(dateStr);
        }catch (ParseException e){
            log.error("日期转化错误："+dateStr,e);
        }
        return null;
    }
    /**
     * 指定模式的时间格式
     * @param pattern
     * @return
     */
    public static SimpleDateFormat getSDFormat(String pattern) {
        return new SimpleDateFormat(pattern);
    }
    /**
     * @Author 
     * @Description 获得得几(日/月/年)后(前)的时间,输入输出格式一致
     * @Date 2020/3/6 12:53
     * @Param [dateStr, format, timeVlue, timeUnit]
     * @return java.lang.String
     **/

    public static String getDateAfter(String dateStr, String dateFormat, int timeVlue, String timeUnit) {
        try {
            SimpleDateFormat format = getSDFormat(dateFormat);
            Date date1 = format.parse(dateStr);
            Date date2 = getDateAfter(date1,timeVlue,timeUnit);
            return format.format(date2);
        } catch (Exception e) {
            log.error("根据参数获取指定时间间隔的日期失败",e);
        }

        return null;
    }
    /**
     * @Author 
     * @Description 获得得几(日/月/年)后(前)的时间,输入输出格式一致
     * @Date 2020/3/6 12:53
     * @Param [dateStr, format, timeVlue, timeUnit]
     * @return java.lang.String
     **/

    public static java.sql.Date getDateAfter(Date date, int timeVlue, String timeUnit) {
        try {
            Calendar now = initCalendar(date);
            if (timeUnit == null || timeUnit.trim().equals("")) {
                log.error("时间单位[" + timeUnit + "]为空");
                return null;
            } else {
                timeUnit = timeUnit.trim();
                if ("D".equalsIgnoreCase(timeUnit)) {
                    now.add(Calendar.DATE, timeVlue);
                } else if ("W".equalsIgnoreCase(timeUnit)) {
                    now.add(Calendar.DATE, timeVlue*7);
                } else if ("M".equalsIgnoreCase(timeUnit)) {
                    now.add(Calendar.MONTH, timeVlue);
                } else if ("Y".equalsIgnoreCase(timeUnit)) {
                    now.add(Calendar.YEAR, timeVlue);
                } else {
                    log.error("未知的期限单位");
                    return null;
                }
            }
            return new java.sql.Date(now.getTime().getTime());
        } catch (Exception e) {
            log.error("根据参数获取指定时间间隔的日期失败",e);
        }

        return null;
    }

    public static Calendar initCalendar(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }
    /**
     * 将日期转化为指定格式
     * @param date 需要转化的日期
     * @param dateFormat 指定的日期格式
     * @return 日期字符串
     */
    public static String dateToStr(Date date , String dateFormat){
        if(date!=null){
            return getSDFormat(dateFormat).format(date);
        }
        return null;
    }
    public static void main(String[] args) {
        String str = "2020-08-01";
//        System.out.println("年:" + nextPriodTimeNoAddition(str, "y"));
//        System.out.println("月:" + nextPriodTimeNoAddition(str, "MM"));
//        System.out.println("日:" + nextPriodTimeNoAddition(str, "d"));
//        System.out.println("时:" + nextPriodTimeNoAddition(str, "h"));
//        System.out.println("分:" + nextPriodTimeNoAddition(str, "m"));
//        System.out.println("秒:" + nextPriodTimeNoAddition(str, "s"));
        System.out.println("秒:" + nextOrBeforPriodTime(str, -1,"MM"));
    }
}
