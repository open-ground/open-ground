package io.github.openground.common.filter;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import io.github.openground.base.utils.AESUtil;
import io.github.openground.base.utils.SM4Utils;
import io.github.openground.base.utils.SignUtil;
import io.github.openground.common.filter.config.RequestFilterProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

/**
 * 通用请求过滤器 — Token校验 + 解密验签 + URL 非法字符检查
 *
 * <p>支持 Token 校验（通过 {@link TokenCheckService} SPI）、AES/SM4 解密、签名验证、
 * GET 参数篡改检查、SQL 注入拦截等功能。</p>
 * <p>纯配置驱动，通过 {@code ground.security.request-filter.enabled=true} 启用。</p>
 *
 * <h3>跳过检查的场景</h3>
 * <ul>
 *   <li>请求头 {@code encrypt: false}</li>
 *   <li>所有开关未开启（tokenCheckEnabled=false && urlRegularEnabled=false && decryptEnabled=false）</li>
 *   <li>Content-Type 为 {@code multipart/form-data}</li>
 * </ul>
 *
 * @version 1.0
 */
@Slf4j
public class CommonRequestFilter implements Filter {

    private static final String SM4 = "SM4";
    private static final String AES = "AES";
    private static final String FALSE = "false";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";

    private final RequestFilterProperties properties;

    /**
     * Token 校验服务（由业务模块 SPI 实现）
     */
    private TokenCheckService tokenCheckService;

    public CommonRequestFilter(RequestFilterProperties properties) {
        this.properties = properties;
    }

    /**
     * 设置 Token 校验服务
     *
     * @param tokenCheckService Token 校验 SPI 实现
     */
    public void setTokenCheckService(TokenCheckService tokenCheckService) {
        this.tokenCheckService = tokenCheckService;
    }

    /**
     * 白名单检查，子类可覆盖以添加自定义白名单逻辑
     *
     * @param request 当前 HTTP 请求
     * @return true 表示放行，不执行任何检查
     */
    protected boolean isWhiteList(HttpServletRequest request) {
        return false;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        Date start = new Date();
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setContentType("application/json;charset=utf-8");

        // 1. 白名单放行
        if (isWhiteList(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 请求头 encrypt=false 跳过检查
        if (FALSE.equals(request.getHeader("encrypt"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 所有开关未开启，跳过
        if (!properties.isTokenCheckEnabled() && !properties.isUrlRegularEnabled() && !properties.isDecryptEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. multipart/form-data 不检查
        String contentType = request.getContentType();
        if (StrUtil.isNotBlank(contentType) && contentType.contains("multipart/form-data")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("begin do CommonRequestFilter: {}", request.getRequestURI());

        // 5. 包装请求体（可重复读取）
        BodyReaderHttpServletRequestWrapper requestWrapper = new BodyReaderHttpServletRequestWrapper(request);

        // 6. 读取请求体
        String requestStr = readBody(request, requestWrapper);

        // 7. Token 校验（由业务模块 SPI 实现，如 Feign 调用 auth 服务）
        if (properties.isTokenCheckEnabled()) {
            if (!handleTokenCheck(request, response)) {
                // Token 校验失败，已输出错误响应
                return;
            }
        }

        // 8. 解密 & 验签
        if (properties.isDecryptEnabled()) {
            requestStr = handleDecryptAndSign(requestStr, requestWrapper, request, response);
            if (requestStr == null) {
                // 解密/验签失败，已输出错误响应
                return;
            }
        }

        // 9. URL 非法字符检查
        if (properties.isUrlRegularEnabled()) {
            urlRegularCheck(request, requestStr);
        }

        log.debug("end do CommonRequestFilter, time: {}ms", System.currentTimeMillis() - start.getTime());
        filterChain.doFilter(requestWrapper, response);
    }

    /**
     * Token 校验
     *
     * @return true 校验通过，false 校验失败（已输出错误响应）
     */
    private boolean handleTokenCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (tokenCheckService == null) {
            log.error("Token 校验已启用但未注入 TokenCheckService 实现 Bean");
            writeError(response, "token认证失败：未配置Token校验服务");
            return false;
        }
        try {
            tokenCheckService.check(request);
            return true;
        } catch (Exception e) {
            log.error("token验证异常/URL访问权限不足：", e);
            if (e.getMessage() != null && e.getMessage().contains("URL访问权限不足")) {
                writeError(response, "URL访问权限不足");
            } else {
                writeError(response, "token认证失败");
            }
            return false;
        }
    }

    /**
     * 读取请求体内容
     */
    private String readBody(HttpServletRequest request, BodyReaderHttpServletRequestWrapper requestWrapper) {
        String requestStr = "";
        if (POST.equalsIgnoreCase(request.getMethod()) || DELETE.equalsIgnoreCase(request.getMethod())) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(requestWrapper.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                requestStr = sb.toString();
            } catch (IOException e) {
                log.error("读取请求体流异常:", e);
            }
        } else {
            // GET/DELETE 从 header 获取加密数据
            requestStr = request.getHeader("EncryptData");
        }
        return requestStr;
    }

    /**
     * 解密 & 验签
     *
     * @return 解密后的请求体字符串，解密/验签失败返回 null
     */
    private String handleDecryptAndSign(String requestStr, BodyReaderHttpServletRequestWrapper requestWrapper,
                                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.debug("start request decrypt, use {} to decrypt", properties.getAlgorithm());

        try {
            if (GET.equalsIgnoreCase(request.getMethod()) && ObjectUtils.isEmpty(request.getHeader("EncryptData"))) {
                writeError(response, "请求参数不正确: GET请求header必须增加EncryptData");
                return null;
            }

            // 解密
            requestStr = decryptRequest(requestStr, requestWrapper);
            if (requestStr == null) {
                writeError(response, "请求报文解密失败");
                return null;
            }

            // GET 参数篡改检查
            checkGetParam(request, requestStr);

            // 验签
            if (!signCheck(requestStr, request)) {
                writeError(response, "验签失败");
                return null;
            }
        } catch (Exception e) {
            log.error("解密/验签处理异常:", e);
            writeError(response, e.getMessage());
            return null;
        }

        return requestStr;
    }

    /**
     * 解密请求报文
     */
    private String decryptRequest(String requestStr, BodyReaderHttpServletRequestWrapper requestWrapper) {
        try {
            String algorithm = properties.getAlgorithm().toUpperCase();
            if (AES.equals(algorithm)) {
                requestStr = AESUtil.decrypt(properties.getEncryptKey(), properties.getEncryptIv(), requestStr);
            } else if (SM4.equals(algorithm)) {
                requestStr = SM4Utils.decrypt(properties.getEncryptKey(), properties.getEncryptIv(), requestStr);
            } else {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "not support algorithm: " + algorithm);
            }
        } catch (Exception e) {
            log.error("请求报文解密失败:", e);
            return null;
        }
        log.debug("after decryption request str is: {}", requestStr);
        // 将解密后的内容重新放入请求体
        requestWrapper.setBody(requestStr.getBytes(StandardCharsets.UTF_8));
        return requestStr;
    }

    /**
     * 检查 GET 参数是否被篡改
     */
    private void checkGetParam(HttpServletRequest request, String requestStr) {
        if (!GET.equalsIgnoreCase(request.getMethod())) {
            return;
        }
        JSONObject json = JSONObject.parseObject(requestStr);
        java.util.Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            String value = request.getParameter(name);
            String jsonValue = json.getString(name);
            if (jsonValue == null) {
                jsonValue = "";
            }
            if (!Objects.equals(value, jsonValue) && !jsonValue.contains(value)) {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "请求参数被篡改");
            }
        }
    }

    /**
     * 验签 — 检查 macValue 一致性和 Sign 签名
     *
     * @return true 验签通过
     */
    private boolean signCheck(String requestStr, HttpServletRequest request) {
        // 检查越权：比对 token 与 macValue
        JSONObject json = JSONObject.parseObject(requestStr);
        JSONObject sysHead = json.getJSONObject("sysHead");
        if (sysHead == null) {
            log.error("请求报文缺少 sysHead");
            return false;
        }

        String timeStr = sysHead.getString("tranDate") + sysHead.getString("tranTimestamp");
        long timestamp = DateUtil.parse(timeStr.substring(0, 14), DatePattern.PURE_DATETIME_PATTERN).getTime();
        String token = request.getHeader("Authorization");
        String macValue = sysHead.getString("macValue");
        if (!StringUtils.pathEquals(token, macValue)) {
            log.error("伪造token或越权访问请求");
            return false;
        }

        // 验签
        String srcRequest = token + requestStr;
        String sign = request.getHeader("Sign");
        try {
            int verifyRes = SignUtil.verify(sign, srcRequest, timestamp,
                    properties.getSignExpire(), properties.getEncryptKey(), properties.getEncryptIv());
            if (verifyRes < 0) {
                log.error("签名已过期");
                return false;
            } else if (verifyRes == 0) {
                log.error("签名无效");
                return false;
            }
        } catch (Exception e) {
            log.error("签名验证失败:", e);
            return false;
        }
        return true;
    }

    /**
     * URL 非法字符检查（SQL 注入拦截）
     */
    private void urlRegularCheck(HttpServletRequest request, String requestStr) {
        log.debug("start request Illegal character check");
        String regex = getRegular();
        log.debug("正则表达式：{}", regex);

        String flag = "%20";

        // 检查 URL
        String url = request.getRequestURL().toString();
        if (StrUtil.isNotBlank(url)) {
            url = url.replace(flag, " ");
            log.debug("URL路径：{}", url);
            if (java.util.regex.Pattern.matches(regex, url)) {
                log.error("URL请求异常");
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "非法请求");
            }
        }

        // 检查 URL 参数
        String queryString = request.getQueryString();
        if (StrUtil.isNotBlank(queryString)) {
            queryString = queryString.replace(flag, " ");
            log.debug("路径请求参数：{}", queryString);
            if (java.util.regex.Pattern.matches(regex, queryString)) {
                log.error("URL路径参数请求异常");
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "非法请求");
            }
        }

        // 检查请求体
        if (StrUtil.isNotBlank(requestStr)) {
            log.debug("请求体参数：{}", requestStr);
            if (java.util.regex.Pattern.matches(regex, requestStr)) {
                log.error("请求体参数异常");
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "非法请求");
            }
        }
    }

    /**
     * 从 classpath:regular.txt 读取非法字符正则
     */
    private static String getRegular() {
        Resource resource = new ClassPathResource("regular.txt");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String data;
            while ((data = br.readLine()) != null) {
                sb.append(data).append("|");
            }
        } catch (Exception e) {
            log.error("读取 regular.txt 异常:", e);
            return ".*(delete|update|insert|drop|alter|truncate|exec|script|alert|eval).*";
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1); // 去掉末尾 |
        }
        return ".*(" + sb + ").*";
    }

    /**
     * 输出错误响应到客户端
     */
    private void writeError(HttpServletResponse response, String message) throws IOException {
        JSONObject out = new JSONObject();
        JSONObject sysHead = new JSONObject();
        sysHead.put("retStatus", "F");
        JSONObject ret = new JSONObject();
        ret.put("retCode", "999999");
        ret.put("retMsg", message);
        out.put("sysHead", sysHead);
        out.put("ret", ret);
        response.getWriter().write(out.toJSONString());
    }
}
