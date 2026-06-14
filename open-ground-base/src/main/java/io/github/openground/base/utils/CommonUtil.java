package io.github.openground.base.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用工具类：金额校验、日期转换、补零等
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Component
public class CommonUtil {

    public static final String yyMMdd = "yyMMdd";
    public static final String yyyyMMdd = "yyyyMMdd";
    public static final String yyyy_MM_dd = "yyyy-MM-dd";
    public static final String HHmmssSSS = "HHmmssSSS";
    public static final String HHmmss = "HH:mm:ss";
    public static final String MMdd = "MMdd";
    public static final String yyyyMMddHHmm = "yyyyMMddHH:mm";
    public static final String yyyyMMddHHmmss = "yyyyMMdd HH:mm:ss";
    public static final String yyyy_MM_ddHHmmss = "yyyy-MM-dd HH:mm:ss";
    public static final String HHmm = "HH:mm";
    public static final String yyyyMMddHHmmssS = "yyyyMMddHHmmssS";
    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    /**
     * 金额校验（正数，最多两位小数）
     *
     * @param str 校验的字符串
     * @return 是否合法金额
     */
    public static boolean checkAmt(String str) {
        if (null == str || "".equals(str)) {
            return false;
        }
        String eL = "^(([1-9]{1}[0-9]{0,12})|([0]{1}))(\\.([0-9]){1,2})?$";
        Pattern p = Pattern.compile(eL);
        Matcher m = p.matcher(str);
        return m.matches();
    }

    /**
     * 字符串是否由纯数字组成
     *
     * @param str 校验的字符串
     * @return 是否纯数字
     */
    public static boolean isNumeric(String str) {
        String eL = "^([0-9]+)$";
        Pattern p = Pattern.compile(eL);
        Matcher m = p.matcher(str);
        return m.matches();
    }

    /**
     * 日期正则检查（yyyyMMdd，8 位）
     *
     * @param checkValue 日期字符串
     * @return 是否合法日期
     */
    public static boolean checkDate(String checkValue) {
        if (null == checkValue || "".equals(checkValue)) {
            return false;
        }
        if (checkValue.length() != 8) {
            return false;
        }
        String str = checkValue.substring(0, 4) + "-" + checkValue.substring(4, 6) + "-" + checkValue.substring(6, 8);
        String eL = "^(?:(?!0000)[0-9]{4}-(?:(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-8])|(?:0[13-9]|1[0-2])"
                + "-(?:29|30)|(?:0[13578]|1[02])-31)|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]"
                + "|[2468][048]|[13579][26])00)-02-29)$";
        Pattern p = Pattern.compile(eL);
        Matcher m = p.matcher(str);
        return m.matches();
    }

    /**
     * 得到当前日期
     *
     * @param formateStr 日期格式
     * @return 当前日期字符串
     */
    public static String getCurrDate(String formateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        return sdf.format(new Date());
    }

    /**
     * 将日期格式化为字符串
     *
     * @param date       要格式化的日期
     * @param formateStr 日期格式
     * @return 格式化后的日期字符串
     */
    public static String getDate2String(Date date, String formateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        return sdf.format(date);
    }

    /**
     * 8 位 String 日期转 10 位 String 日期（yyyyMMdd → yyyy-MM-dd）
     *
     * @param startDate 日期字符串
     * @return 转换后的日期字符串
     */
    public static String getString2String(String startDate) {
        startDate = startDate.replaceAll("-", "");
        return startDate.substring(0, 4) + "-" + startDate.substring(4, 6) + "-" + startDate.substring(6);
    }

    /**
     * 格式化 string 日期
     *
     * @param startDate  日期字符串
     * @param formateStr 日期格式
     * @return 格式化后的日期字符串
     * @throws ParseException 解析异常
     */
    public static String getStringDate(String startDate, String formateStr) throws ParseException {
        Date d = getString2Date(startDate, formateStr);
        return getDate2String(d, formateStr);
    }

    /**
     * 将字符串按指定格式转换为日期类型
     *
     * @param strDate    日期字符串
     * @param formateStr 日期格式
     * @return 日期对象
     * @throws ParseException 解析异常
     */
    public static Date getString2Date(String strDate, String formateStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        return sdf.parse(strDate);
    }

    /**
     * 将一个日期类型转换为指定格式的日期类型
     *
     * @param date       要转换的日期
     * @param formateStr 日期格式
     * @return 转换后的日期对象
     * @throws ParseException 解析异常
     */
    public static Date getDate2Date(Date date, String formateStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        return getString2Date(sdf.format(date), formateStr);
    }

    /**
     * 指定格式日期比较（前者小于或等于后者返回 true）
     *
     * @param strdate1   日期 1
     * @param strdate2   日期 2
     * @param formateStr 日期格式
     * @return 比较结果
     */
    public static boolean compareDate(String strdate1, String strdate2, String formateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        try {
            Date date1 = sdf.parse(strdate1);
            Date date2 = sdf.parse(strdate2);
            return date1.compareTo(date2) < 1;
        } catch (ParseException e) {
            log.error("日期格式转换失败", e);
        }
        return false;
    }

    /**
     * 指定格式日期比较（前者小于后者返回 true）
     *
     * @param strdate1   日期 1
     * @param strdate2   日期 2
     * @param formateStr 日期格式
     * @return 比较结果
     */
    public static boolean compareDateBefore(String strdate1, String strdate2, String formateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat(formateStr);
        try {
            Date date1 = sdf.parse(strdate1);
            Date date2 = sdf.parse(strdate2);
            return date1.before(date2);
        } catch (ParseException e) {
            log.error("日期格式转换失败", e);
        }
        return false;
    }

    /**
     * 不够位数的在前面补 0，保留指定长度
     *
     * @param code 数字
     * @param num  长度
     * @return 补零后的字符串
     */
    public static String autoGenericCode(int code, int num) {
        return String.format("%0" + num + "d", code);
    }

    /**
     * 按索引位置替换字符
     *
     * @param string    源字符串
     * @param index     替换位置
     * @param character 目标字符
     * @return 替换后的字符串
     */
    public static String replaceByIndex(String string, int index, char character) {
        if (string.length() < 1) return null;
        char array[] = string.toCharArray();
        if (index > array.length) return null;
        for (int i = 0; i < array.length; i++) {
            if (i == index) {
                array[i] = character;
            }
        }
        return new String(array);
    }

    /**
     * 获得服务器当前日期及时间，格式：yyyyMMddHHmmss
     *
     * @return 日期时间字符串
     */
    public static String getDateTime() {
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar cale = Calendar.getInstance();
        try {
            return sdf2.format(cale.getTime());
        } catch (Exception e) {
            log.error("日期转换失败", e);
            return "";
        }
    }

    /**
     * 将金额除以 100（去掉 RMB 前缀）
     *
     * @param str 金额字符串（可能含 RMB 前缀）
     * @return 除以 100 后的金额
     */
    public static String getRMB(String str) {
        String str1;
        if (str.length() >= 3) {
            str1 = str.substring(0, 3);
            if ("RMB".equals(str1)) {
                str = str.substring(3);
            }
        }
        try {
            BigDecimal bdValue = new BigDecimal(str);
            bdValue = bdValue.divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            str = bdValue.toPlainString();
        } catch (Exception e) {
            log.error("金额转换失败", e);
        }
        return str;
    }

    /**
     * 将金额乘以 100 后加上 RMB 前缀
     *
     * @param str 金额
     * @return RMB 前缀 + 乘以 100 后的金额
     */
    public static String setRMB(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        try {
            BigDecimal bdValue = new BigDecimal(str);
            bdValue = bdValue.multiply(new BigDecimal("100"));
            str = String.valueOf(bdValue.longValue());
            str = "RMB" + liftFillZero(str, 15);
        } catch (Exception e) {
            log.error("金额乘以100失败", e);
        }
        return str;
    }

    /**
     * 左补零
     *
     * @param count  数据
     * @param length 目标长度
     * @return 补零后的字符串
     */
    public static String liftFillZero(String count, int length) {
        if (count == null || "".equals(count)) {
            count = "0";
        }
        int j = length - count.length();
        for (int i = 0; i < j; i++) {
            count = "0" + count;
        }
        return count;
    }

    /**
     * 将金额乘以 100（带币种）
     *
     * @param amt 金额
     * @param ccy 币种
     * @return 币种 + 乘以 100 后的金额
     */
    public static String getAmt(String amt, String ccy) {
        if (amt == null || amt.length() == 0) {
            return amt;
        }
        try {
            BigDecimal bdValue = new BigDecimal(amt);
            bdValue = bdValue.multiply(new BigDecimal("100"));
            amt = String.valueOf(bdValue.longValue());
            amt = ccy + liftFillZero(amt, 15);
        } catch (Exception e) {
            log.error("金额乘以100失败", e);
        }
        return amt;
    }

    /**
     * 将金额乘以 100
     *
     * @param amt 金额
     * @return 乘以 100 后的金额
     */
    public static String getAmt(String amt) {
        if (amt == null || amt.length() == 0) {
            return amt;
        }
        try {
            BigDecimal bdValue = new BigDecimal(amt);
            bdValue = bdValue.multiply(new BigDecimal("100"));
            amt = String.valueOf(bdValue.longValue());
            amt = liftFillZero(amt, 15);
        } catch (Exception e) {
            log.error("金额乘以100失败", e);
        }
        return amt;
    }

    /**
     * 将金额除以 100
     *
     * @param amt 金额
     * @return 除以 100 后的金额
     */
    public static String getdivideAmt(String amt) {
        if (amt == null || amt.length() == 0) {
            return amt;
        }
        try {
            BigDecimal bdValue = new BigDecimal(amt);
            bdValue = bdValue.divide(new BigDecimal("100"));
            amt = bdValue.toPlainString();
        } catch (Exception e) {
            log.error("金额除以100失败", e);
        }
        return amt;
    }

    /**
     * 将金额除以 100（保留两位小数，四舍五入）
     *
     * @param amt 金额
     * @return 除以 100 后的金额
     */
    public static String getdivideAmtSign(String amt) {
        if (amt == null || amt.length() == 0) {
            return amt;
        }
        try {
            BigDecimal bdValue = new BigDecimal(amt);
            bdValue = bdValue.divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            amt = bdValue.toPlainString();
        } catch (Exception e) {
            log.error("金额除以100失败", e);
        }
        return amt;
    }

    /**
     * 字符串转换为 16 进制
     *
     * @param strData 字符串
     * @return 16 进制字符串
     * @throws Exception 转换异常
     */
    public static String toHexString(String strData) throws Exception {
        byte[] byteValue = strData.getBytes();
        String[] hexValueArry = new String[byteValue.length];
        for (int i = 0; i < byteValue.length; i++) {
            hexValueArry[i] = Integer.toHexString(byteValue[i]).toUpperCase();
        }
        StringBuilder reStrValue = new StringBuilder();
        for (String s : hexValueArry) {
            reStrValue.append(s);
        }
        return reStrValue.toString();
    }

    /**
     * 从 HashMap 中按照键获取值，不存在时返回空字符串
     *
     * @param params Map
     * @param key    键
     * @return 值或空字符串
     */
    public static String getStringValueFromHashMap(Map<String, Object> params, String key) {
        if (params.containsKey(key) && params.get(key) != null) {
            return params.get(key).toString();
        }
        return "";
    }

    /**
     * 从 HashMap 中按照键获取金额值，不存在时返回 "0"
     *
     * @param params Map
     * @param key    键
     * @return 值或 "0"
     */
    public static String getStringValueFromHashMapForMoney(Map<String, Object> params, String key) {
        if (params.containsKey(key) && params.get(key) != null) {
            return params.get(key).toString();
        }
        return "0";
    }

    /**
     * 从 HashMap 中按照键获取 Double 值，不存在时返回 0.0
     *
     * @param params Map
     * @param key    键
     * @return Double 值
     */
    public static Double getDoubleValueFromHashMap(Map<String, Object> params, String key) {
        if (params.containsKey(key) && params.get(key) != null) {
            return Double.valueOf(params.get(key).toString());
        }
        return 0.0;
    }

    /**
     * 获取非法正则表达式字符串（从 classpath:regular.txt 读取）
     *
     * @return 正则表达式
     */
    public static String getRegular() {
        Resource resource = new ClassPathResource("regular.txt");
        String data;
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        StringBuilder zjStr = new StringBuilder();
        try {
            is = resource.getInputStream();
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            while ((data = br.readLine()) != null) {
                zjStr.append(data).append("|");
            }
        } catch (Exception e) {
            log.error("读取文件流异常", e);
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {
            }
            try {
                if (isr != null) isr.close();
            } catch (Exception ignored) {
            }
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
        if (zjStr.length() == 0) return "";
        return ".*(" + zjStr.substring(0, zjStr.length() - 1) + ").*";
    }
}
