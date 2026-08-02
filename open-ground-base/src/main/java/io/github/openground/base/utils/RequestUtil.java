package io.github.openground.base.utils;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request 工具类：获取当前请求信息、Token、Header 等
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class RequestUtil {

    /**
     * 线程本地变量，用于存放授权信息（Token）
     */
    public static final ThreadLocal<String> tokenStrLocal = ThreadLocal.withInitial(() -> "");

    /**
     * 存放当前请求的法人信息
     */
    public static final ThreadLocal<String> companyLocal = ThreadLocal.withInitial(() -> "");

    /**
     * 获取当前请求法人信息
     *
     * @return 法人信息
     */
    public static String getCompany() {
        return companyLocal.get();
    }

    /**
     * 获取当前请求中的令牌信息
     * 从请求头中获取令牌值
     */
    public static String getToken() {
        return getTokenStr(getRequest());
    }

    /**
     * 从请求中获取授权信息
     *
     * @param request HTTP 请求
     * @return Authorization 值
     */
    public static String getTokenStr(HttpServletRequest request) {
        String auth = getHeaders(request).get("Authorization");
        if (auth == null || auth.trim().isEmpty()) {
            auth = getHeaders(request).get("authorization");
        }
        return auth;
    }

    /**
     * 获取当前 HttpServletRequest
     *
     * @return HttpServletRequest，无法获取时返回 null
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return ((ServletRequestAttributes) attributes).getRequest();
    }

    /**
     * 获取 HttpServletRequest 的所有 header
     *
     * @param request HTTP 请求
     * @return header Map
     */
    public static Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }
        return map;
    }
}
