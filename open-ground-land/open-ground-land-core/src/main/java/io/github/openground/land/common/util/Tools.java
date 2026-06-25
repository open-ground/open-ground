package io.github.openground.land.common.util;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import io.github.openground.land.common.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 说明：常用工具
 * 创建人：
 * 修改时间：2015年11月24日
 */
@Slf4j
public class Tools {
    /**
     * 随机生成六位数验证码
     *
     * @return
     */
    public static int getRandomNum() {
        SecureRandom secRandom = new SecureRandom();
        return secRandom.nextInt(900000) + 100000;//(Math.random()*(999999-100000)+100000)
    }

    /**
     * 检测字符串是否不为空(null,"","null")
     *
     * @param s
     * @return 不为空则返回true，否则返回false
     */
    public static boolean notEmpty(String s) {
        return s != null && !"".equals(s) && !"null".equals(s);
    }

    /**
     * 检测字符串是否为空(null,"","null")
     *
     * @param s
     * @return 为空则返回true，不否则返回false
     */
    public static boolean isEmpty(String s) {
        return s == null || "".equals(s) || "null".equals(s);
    }

    /**
     * 字符串转换为字符串数组
     *
     * @param str        字符串
     * @param splitRegex 分隔符
     * @return
     */
    public static String[] str2StrArray(String str, String splitRegex) {
        if (isEmpty(str)) {
            return null;
        }
        return str.split(splitRegex);
    }

    /**
     * 用默认的分隔符(,)将字符串转换为字符串数组
     *
     * @param str 字符串
     * @return
     */
    public static String[] str2StrArray(String str) {
        return str2StrArray(str, ",\\s*");
    }

    /**
     * 按照yyyy-MM-dd HH:mm:ss的格式，日期转字符串
     *
     * @param date
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String date2Str(Date date) {
        return date2Str(date, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 按照yyyy-MM-dd HH:mm:ss的格式，字符串转日期
     *
     * @param date
     * @return
     */
    public static Date str2Date(String date) {
        if (notEmpty(date)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                return sdf.parse(date);
            } catch (ParseException e) {
                log.error("", e);
            }
            return new Date();
        } else {
            return null;
        }
    }

    /**
     * 按照参数format的格式，日期转字符串
     *
     * @param date
     * @param format
     * @return
     */
    public static String date2Str(Date date, String format) {
        if (date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.format(date);
        } else {
            return "";
        }
    }

    /**
     * 把时间根据时、分、秒转换为时间段
     *
     * @param strDate
     */
    public static String getTimes(String strDate) {
        String resultTimes = "";

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now;

        try {
            now = new Date();
            Date date = df.parse(strDate);
            long times = now.getTime() - date.getTime();
            long day = times / (24 * 60 * 60 * 1000);
            long hour = (times / (60 * 60 * 1000) - day * 24);
            long min = ((times / (60 * 1000)) - day * 24 * 60 - hour * 60);
            long sec = (times / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);

            StringBuffer sb = new StringBuffer();
            if (hour > 0) {
                sb.append(hour + "小时前");
            } else if (min > 0) {
                sb.append(min + "分钟前");
            } else {
                sb.append(sec + "秒前");
            }

            resultTimes = sb.toString();
        } catch (ParseException e) {
            log.error("", e);
        }

        return resultTimes;
    }


    /**
     * 获得当前服务器主机IP
     *
     * @return
     * @author jack.zhang
     * @date 2017年3月13日 下午5:08:23
     * @version v_1.0
     */
    public static String getServerIp() {

        String ipAddress = "unknown";
        // 先通过hostname获取
        try {
            String hostIp = InetAddress.getLocalHost().getHostAddress();
            if (hostIp != null && !hostIp.isEmpty() && !"127.0.0.1".equals(hostIp) && hostIp.indexOf(':') == -1) {
                log.debug("获取到本机IP为：" + hostIp);
                return hostIp;
            }
        } catch (UnknownHostException e) {
            log.error("通过主机名获取IP地址失败", e);
        }

        log.debug("遍历所有的网卡获取第一块网卡上的IP");
        try {
            // 遍历所有的网卡获取第一块网卡上的IP
            Map<String, String> ipMap = new HashMap<>();
            Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) interfaces.nextElement();
                Enumeration ipAddrEnum = ni.getInetAddresses();
                while (ipAddrEnum.hasMoreElements()) {
                    InetAddress addr = (InetAddress) ipAddrEnum.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    String ip = addr.getHostAddress();
                    if (ip.indexOf(':') != -1) {
                        // skip the IPv6 addr
                        continue;
                    }
                    log.debug("网卡: " + ni.getName() + ", IP: " + ip);
                    ipMap.put(ni.getName(), ip);
                }
            }
            if (!ipMap.isEmpty()) {
                List<Entry<String, String>> ipsort = new ArrayList<>(ipMap.entrySet());
                Collections.sort(ipsort, new Comparator<Entry<String, String>>() {

                    @Override
                    public int compare(Entry<String, String> o1, Entry<String, String> o2) {
                        // 升序
                        return o1.getKey().compareTo(o2.getKey());
                    }
                });
                String hostIp = ipsort.get(0).getValue();
                log.debug("获取到本机IP为：" + hostIp);
                return hostIp;
            } else {
                log.error("获取本机Ip失败,请联系系统管理员");
            }
        } catch (Exception e) {
            log.error("获取本机Ip失败", e);
        }
        return ipAddress;
    }

    /**
     * 获取客户端的IP
     *
     * @param request
     * @return
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        String unk = "unknown";
        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }

        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }

        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getHeader("x-real-ip");
        }

        if (ip == null || ip.isEmpty() || unk.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip.indexOf(",") != -1) {
            ip = ip.substring(0, ip.indexOf(","));
        }

        //服务器和客户端在一台服务器上,尝试获取真实IP
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
            ip = getServerIp();
            if (unk.equals(ip)) {
                ip = "127.0.0.1";
            }
        }
        return ip;
    }

    /**
     * <p>Description: 获取完整堆栈信息</P>
     *
     * @param ex
     * @return java.lang.String
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2020/7/15 0:21
     */
/*    public static String getExceptionMessage1(Exception ex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(out);
        ex.printStackTrace(pout);
        String ret = new String(out.toByteArray());
        pout.close();
        try {
            out.close();
        } catch (Exception e) {
        }
        return ret;
    }*/

    public static String getExceptionAllinformation(Exception ex) {
        String sOut = "";
        StackTraceElement[] trace = ex.getStackTrace();
        for (StackTraceElement s : trace) {
            sOut += "\tat " + s + "\r\n";
        }
        return sOut;
    }

/*    public static String getTrace(Throwable t) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        t.printStackTrace(writer);
        StringBuffer buffer = stringWriter.getBuffer();
        return buffer.toString();
    }*/

    public static void checkResponse(String res){
        JSONObject resJson = JSONObject.parseObject(res);
        if("F".equals(resJson.getJSONObject("sysHead").getString("retStatus"))){
            JSONObject ret = resJson.getJSONObject("sysHead").getJSONArray("ret").getJSONObject(0);
            String errCode = ret.getString("retCode");
            String errMsg = ret.getString("retMsg");
            throw  new SystemException(errCode);
        }
    }

    public static String tuoMin(String data) {
        if (ObjectUtil.isNotNull(data)) {
            if(data.length() <= 2 ){
                data = tuoMin(1, 0, data);
            } else {
                data = tuoMin(1, 1, data);
            }
        }
        return data;
    }
    public static String tuoMin(int start, int end, String data) {
        if (data == null || data.trim() == null || data.length() < start + end) {
            return data;// 不符合脱敏条件，原数据返回，也可以抛出异常
        }
        // 先截取保留的前面字符
        StringBuffer sb = new StringBuffer(data.substring(0, start));
        // 每遍历一位加一个*
        for (int i = start; i < data.length() - end; i++) {
            sb.append("*");
        }
        // 添加后面需要保留字符
        sb.append(data.substring(data.length() - end));
        return sb.toString();
    }

    public static void main(String[] args) {
        log.info(tuoMin(null));
    }

}
