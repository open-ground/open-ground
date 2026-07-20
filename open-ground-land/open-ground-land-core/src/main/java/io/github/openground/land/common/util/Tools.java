package io.github.openground.land.common.util;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import io.github.openground.land.common.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

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
     * 获取本机服务器IP，支持多网卡（有线+无线）和双栈（IPv4+IPv6）环境。
     * <p>
     * 优先级策略：
     * 1. 优先从有线网卡中选取IPv4私有地址（10.x > 172.16-31.x > 192.168.x）
     * 2. 再从无线网卡中选取IPv4私有地址
     * 3. 有线网卡的IPv4公网地址
     * 4. 无线网卡的IPv4公网地址
     * 5. 有线网卡的IPv6全局地址（排除链路本地fe80::和ULA fd00::）
     * 6. 无线网卡的IPv6全局地址
     * 7. IPv6 ULA地址（fd00::/8）
     * 8. 兜底返回第一个非回环地址
     * </p>
     *
     * @return 本机IP
     * @author jack.zhang
     * @date 2017年3月13日 下午5:08:23
     * @version v_3.0
     */
    public static String getServerIp() {
        String ipAddress = "unknown";

        // 收集所有非回环IP地址，区分有线/无线、IPv4/IPv6
        List<IpInfo> ipList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // 跳过未启用的网卡
                if (!ni.isUp()) {
                    continue;
                }
                boolean wired = isWiredInterface(ni);
                Enumeration<InetAddress> ipAddrEnum = ni.getInetAddresses();
                while (ipAddrEnum.hasMoreElements()) {
                    InetAddress addr = ipAddrEnum.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    boolean ipv4 = ip.indexOf(':') == -1;
                    log.debug("网卡: {} ({}), IP: {} ({})", ni.getName(), wired ? "有线" : "无线", ip, ipv4 ? "IPv4" : "IPv6");
                    ipList.add(new IpInfo(ip, wired, ipv4));
                }
            }
        } catch (Exception e) {
            log.error("遍历网卡获取IP地址失败", e);
        }

        if (ipList.isEmpty()) {
            log.error("获取本机Ip失败,未找到可用网卡");
            return ipAddress;
        }

        // ===== IPv4 优先策略 =====

        // 策略1：有线网卡 IPv4 私有地址（10.x > 172.16-31.x > 192.168.x）
        String ip = findIpv4Private(ipList, true);
        if (ip != null) {
            log.debug("从有线网卡匹配到IPv4私有地址: {}", ip);
            return ip;
        }

        // 策略2：无线网卡 IPv4 私有地址
        ip = findIpv4Private(ipList, false);
        if (ip != null) {
            log.debug("从无线网卡匹配到IPv4私有地址: {}", ip);
            return ip;
        }

        // 策略3：有线网卡 IPv4 任意地址（含公网IP）
        ip = findIpv4Any(ipList, true);
        if (ip != null) {
            log.debug("从有线网卡匹配到IPv4地址: {}", ip);
            return ip;
        }

        // 策略4：无线网卡 IPv4 任意地址
        ip = findIpv4Any(ipList, false);
        if (ip != null) {
            log.debug("从无线网卡匹配到IPv4地址: {}", ip);
            return ip;
        }

        // ===== IPv6 兜底策略 =====

        // 策略5：有线网卡 IPv6 全局地址（排除链路本地和ULA）
        ip = findIpv6Global(ipList, true);
        if (ip != null) {
            log.debug("从有线网卡匹配到IPv6全局地址: {}", ip);
            return ip;
        }

        // 策略6：无线网卡 IPv6 全局地址
        ip = findIpv6Global(ipList, false);
        if (ip != null) {
            log.debug("从无线网卡匹配到IPv6全局地址: {}", ip);
            return ip;
        }

        // 策略7：IPv6 ULA地址（fd00::/8）
        ip = findIpv6Ula(ipList);
        if (ip != null) {
            log.debug("匹配到IPv6 ULA地址: {}", ip);
            return ip;
        }

        // 策略8：兜底返回第一个地址（优先有线IPv4）
        ipList.sort((o1, o2) -> {
            if (o1.wired != o2.wired) return o1.wired ? -1 : 1;
            if (o1.ipv4 != o2.ipv4) return o1.ipv4 ? -1 : 1;
            return 0;
        });
        String hostIp = ipList.get(0).ip;
        log.debug("使用兜底策略获取到本机IP: {}", hostIp);
        return hostIp;
    }

    /** 从指定类型网卡查找IPv4私有地址 */
    private static String findIpv4Private(List<IpInfo> ipList, boolean wired) {
        for (IpInfo info : ipList) {
            if (info.wired == wired && info.ipv4 && isPrivateA(info.ip)) return info.ip;
        }
        for (IpInfo info : ipList) {
            if (info.wired == wired && info.ipv4 && isPrivateB(info.ip)) return info.ip;
        }
        for (IpInfo info : ipList) {
            if (info.wired == wired && info.ipv4 && isPrivateC(info.ip)) return info.ip;
        }
        return null;
    }

    /** 从指定类型网卡查找任意IPv4地址 */
    private static String findIpv4Any(List<IpInfo> ipList, boolean wired) {
        for (IpInfo info : ipList) {
            if (info.wired == wired && info.ipv4) return info.ip;
        }
        return null;
    }

    /** 从指定类型网卡查找IPv6全局地址（排除链路本地fe80::和ULA fd00::） */
    private static String findIpv6Global(List<IpInfo> ipList, boolean wired) {
        for (IpInfo info : ipList) {
            if (info.wired == wired && !info.ipv4 && !isIpv6LinkLocal(info.ip) && !isIpv6Ula(info.ip)) {
                return info.ip;
            }
        }
        return null;
    }

    /** 查找IPv6 ULA地址（fd00::/8） */
    private static String findIpv6Ula(List<IpInfo> ipList) {
        for (IpInfo info : ipList) {
            if (!info.ipv4 && isIpv6Ula(info.ip)) return info.ip;
        }
        return null;
    }

    /**
     * 判断网卡是否为有线网卡。
     * <p>
     * 判断逻辑：
     * - 通过网卡显示名称判断（包含 Ethernet/Thunderbolt/USB LAN 等关键词为有线）
     * - 通过网卡名称模式判断（Linux: eth/en/em/ens 为有线；macOS: en0 通常为有线）
     * - 排除无线相关名称（wlan/wlp/wl/awdl 等为无线）
     * </p>
     */
    private static boolean isWiredInterface(NetworkInterface ni) {
        String displayName = ni.getDisplayName();
        String name = ni.getName();

        // 通过显示名称判断（macOS 上较可靠）
        if (displayName != null) {
            String lower = displayName.toLowerCase();
            if (lower.contains("ethernet") || lower.contains("thunderbolt")
                    || lower.contains("usb lan") || lower.contains("usb 10/100")
                    || lower.contains("usb 10/100/1000")) {
                return true;
            }
            if (lower.contains("wi-fi") || lower.contains("airport") || lower.contains("wireless")
                    || lower.contains("wlan") || lower.contains("wi-fi")) {
                return false;
            }
        }

        // 通过网卡名称模式判断
        String lowerName = name.toLowerCase();
        // 无线网卡特征
        if (lowerName.startsWith("wlan") || lowerName.startsWith("wlp")
                || lowerName.startsWith("wlx") || lowerName.startsWith("awdl")
                || lowerName.startsWith("llw")) {
            return false;
        }
        // 有线网卡特征（Linux）
        if (lowerName.startsWith("eth") || lowerName.startsWith("em")
                || lowerName.startsWith("ens") || lowerName.startsWith("enp")
                || lowerName.startsWith("bond")) {
            return true;
        }
        // macOS: en0 通常为内置有线/Thunderbolt
        if (lowerName.equals("en0")) {
            return true;
        }

        // 默认认为是有线（服务器环境通常以有线为主）
        return true;
    }

    /** 网卡IP信息 */
    private static class IpInfo {
        final String ip;
        final boolean wired;
        final boolean ipv4;

        IpInfo(String ip, boolean wired, boolean ipv4) {
            this.ip = ip;
            this.wired = wired;
            this.ipv4 = ipv4;
        }
    }

    /** A类私有地址: 10.0.0.0/8 */
    private static boolean isPrivateA(String ip) {
        return ip.startsWith("10.");
    }

    /** B类私有地址: 172.16.0.0/12 */
    private static boolean isPrivateB(String ip) {
        if (!ip.startsWith("172.")) return false;
        try {
            int second = Integer.parseInt(ip.substring(4, ip.indexOf('.', 4)));
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    /** C类私有地址: 192.168.0.0/16 */
    private static boolean isPrivateC(String ip) {
        return ip.startsWith("192.168.");
    }

    /** IPv6链路本地地址: fe80::/10 */
    private static boolean isIpv6LinkLocal(String ip) {
        return ip.toLowerCase().startsWith("fe80:") || ip.toLowerCase().startsWith("fe8");
    }

    /** IPv6 ULA地址: fc00::/7 (实际使用 fd00::/8) */
    private static boolean isIpv6Ula(String ip) {
        String lower = ip.toLowerCase();
        return lower.startsWith("fc") || lower.startsWith("fd");
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
        // 演示：通过系统属性指定优先IP前缀
        // System.setProperty("preferred.ip.prefix", "10.7.");
        String ip = getServerIp();
        System.out.println("获取到本机IP: " + ip);
    }

}
