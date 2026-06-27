package io.github.openground.common.log.aspect;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.github.openground.common.log.config.RequestLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 请求/响应日志切面
 *
 * <p>通过 {@code @Around} 拦截所有 Controller 方法，以 debug 级别输出请求输入参数和响应结果。
 * 格式采用 Unicode 边框，请求和响应分开打印，清晰美观。</p>
 *
 * <p>安全处理：</p>
 * <ul>
 *   <li>Authorization 等敏感 Header 仅显示前 30 字符 + ***</li>
 *   <li>password/secret/token 等敏感字段值替换为 ******</li>
 *   <li>超过 {@code maxBodyLength} 的 body 自动截断</li>
 *   <li>multipart 文件上传不打印文件内容</li>
 * </ul>
 *
 * <p>通过 {@code ground.log.request-log.enabled=true} 启用。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Aspect
public class RequestLogAspect {

    /** 截断提示 */
    private static final String TRUNCATED_SUFFIX = "(truncated)";

    /** 关键 Header 白名单（只输出这些 Header） */
    private static final String[] KEY_HEADERS = {
            "content-type", "authorization", "user-agent", "referer", "origin", "x-forwarded-for"
    };

    /** 敏感字段正则缓存 */
    private Pattern sensitiveFieldPattern;

    /** 敏感字段列表版本号（用于判断是否需要重建正则） */
    private List<String> cachedSensitiveFields;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    /** 配置属性 */
    private RequestLogProperties properties;

    /**
     * 设置配置属性
     *
     * @param properties 请求日志配置
     */
    public void setProperties(RequestLogProperties properties) {
        this.properties = properties;
    }

    /**
     * 环绕通知：拦截所有 Controller 方法，打印请求/响应日志
     *
     * @param joinPoint 连接点
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("execution(* io.github.openground..controller..*.*(..))")
    public Object logRequestResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI();

        // 检查排除 URL
        if (isExcluded(uri)) {
            return joinPoint.proceed();
        }

        String httpMethod = request.getMethod();
        long startTime = System.currentTimeMillis();

        // === 请求日志 ===
        log.debug("{}", buildRequestLog(httpMethod, uri, request, joinPoint));

        // 执行目标方法
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - startTime;
            log.debug("{}", buildErrorLog(httpMethod, uri, cost, e));
            throw e;
        }

        // === 响应日志 ===
        long cost = System.currentTimeMillis() - startTime;
        log.debug("{}", buildResponseLog(httpMethod, uri, result, cost));

        return result;
    }

    /**
     * 构建请求日志
     */
    private String buildRequestLog(String method, String uri, HttpServletRequest request,
                                   ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        appendLine(sb, String.format("  ⇢  %-5s %s", method, uri));

        // Headers（只输出关键 Header）
        appendLine(sb, "  ┌─ Headers ────────────────────────────────────────────────────────────┐");
        Map<String, String> headers = getHeaders(request);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!isKeyHeader(entry.getKey())) {
                continue;
            }
            String value = maskSensitiveHeader(entry.getKey(), entry.getValue());
            appendLine(sb, String.format("   %-18s: %s", entry.getKey(), value));
        }
        appendLine(sb, "  └──────────────────────────────────────────────────────────────────────┘");

        // Body / Parameters
        String body = getRequestParams(request, joinPoint);
        if (body != null && !body.isEmpty()) {
            appendLine(sb, "  ┌─ Body ───────────────────────────────────────────────────────────────┐");
            String maskedBody = maskSensitiveFields(body);
            String displayBody = truncateBody(maskedBody);
            // JSON 格式化输出
            String formattedBody = formatJsonBody(displayBody);
            String[] lines = formattedBody.split("\n");
            for (String line : lines) {
                appendLine(sb, String.format("   %s", line));
            }
            if (isTruncated(maskedBody)) {
                appendLine(sb, String.format("   %s", TRUNCATED_SUFFIX));
            }
            appendLine(sb, "  └──────────────────────────────────────────────────────────────────────┘");
        }

        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        return sb.toString();
    }

    /**
     * 构建响应日志
     */
    private String buildResponseLog(String method, String uri, Object result, long cost) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        int status = 200;
        if (result instanceof ResponseEntity) {
            status = ((ResponseEntity<?>) result).getStatusCode().value();
        }
        appendLine(sb, String.format("  ⇠  %-5s %s  →  %d  (%dms)",
                method, uri, status, cost));

        // Response body
        if (result != null) {
            String body;
            if (result instanceof ResponseEntity) {
                body = JSON.toJSONString(((ResponseEntity<?>) result).getBody(),
                        SerializerFeature.WriteMapNullValue);
            } else {
                body = JSON.toJSONString(result, SerializerFeature.WriteMapNullValue);
            }
            String maskedBody = maskSensitiveFields(body);
            String displayBody = truncateBody(maskedBody);
            // JSON 格式化输出
            String formattedBody = formatJsonBody(displayBody);

            appendLine(sb, "  ┌─ Body ───────────────────────────────────────────────────────────────┐");
            String[] lines = formattedBody.split("\n");
            for (String line : lines) {
                appendLine(sb, String.format("   %s", line));
            }
            if (isTruncated(maskedBody)) {
                appendLine(sb, String.format("   %s", TRUNCATED_SUFFIX));
            }
            appendLine(sb, "  └──────────────────────────────────────────────────────────────────────┘");
        }

        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        return sb.toString();
    }

    /**
     * 构建异常日志
     */
    private String buildErrorLog(String method, String uri, long cost, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        appendLine(sb, String.format("  ⇠  %-5s %s  →  ERROR  (%dms)",
                method, uri, cost));
        appendLine(sb, "  ┌─ Exception ──────────────────────────────────────────────────────────┐");
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        String[] lines = msg.split("\n");
        for (String line : lines) {
            appendLine(sb, String.format("   %s", line));
        }
        appendLine(sb, "  └──────────────────────────────────────────────────────────────────────┘");
        appendLine(sb, "════════════════════════════════════════════════════════════════════════════════");
        return sb.toString();
    }

    /**
     * 获取请求 Header 列表
     */
    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getHeader(name));
        }
        return map;
    }

    /**
     * 脱敏敏感 Header 值
     */
    private String maskSensitiveHeader(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int previewLen = properties.getHeaderPreviewLength();
        for (String sensitive : properties.getSensitiveHeaders()) {
            if (key.equalsIgnoreCase(sensitive)) {
                if (value.length() <= previewLen) {
                    return value.substring(0, Math.min(10, value.length())) + "***";
                }
                return value.substring(0, previewLen) + "***";
            }
        }
        return value;
    }

    /**
     * 脱敏 JSON 中的敏感字段值
     */
    private String maskSensitiveFields(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        Pattern pattern = getSensitiveFieldPattern();
        return pattern.matcher(json).replaceAll("\"$1\":\"" + properties.getMaskText() + "\"");
    }

    /**
     * 获取敏感字段正则（带缓存，配置变更时自动重建）
     */
    private Pattern getSensitiveFieldPattern() {
        List<String> currentFields = properties.getSensitiveFields();
        if (sensitiveFieldPattern == null || !currentFields.equals(cachedSensitiveFields)) {
            String fieldPattern = currentFields.stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"));
            sensitiveFieldPattern = Pattern.compile(
                    "(?i)\"(" + fieldPattern + ")\"\\s*:\\s*\"([^\"]+)\"");
            cachedSensitiveFields = currentFields;
        }
        return sensitiveFieldPattern;
    }

    /**
     * 截断过长的 body
     */
    private String truncateBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= properties.getMaxBodyLength()) {
            return body;
        }
        return new String(bytes, 0, properties.getMaxBodyLength(), StandardCharsets.UTF_8);
    }

    /**
     * 判断 body 是否被截断
     */
    private boolean isTruncated(String body) {
        if (body == null) {
            return false;
        }
        return body.getBytes(StandardCharsets.UTF_8).length > properties.getMaxBodyLength();
    }

    /**
     * 获取请求参数
     * <p>优先从方法参数获取（更可靠，支持已消费的流），回退到从 request 获取。</p>
     */
    private String getRequestParams(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        // 优先从方法参数获取
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            StringBuilder sb = new StringBuilder("{");
            boolean hasParam = false;
            for (int i = 0; i < args.length; i++) {
                // 跳过 Servlet 和文件上传参数
                if (args[i] instanceof HttpServletRequest || args[i] instanceof HttpServletResponse
                        || args[i] instanceof MultipartFile || args[i] instanceof MultipartFile[]) {
                    continue;
                }
                if (hasParam) {
                    sb.append(", ");
                }
                String paramName = parameters[i].getName();
                String paramValue = JSON.toJSONString(args[i], SerializerFeature.WriteMapNullValue);
                sb.append("\"").append(paramName).append("\":").append(paramValue);
                hasParam = true;
            }
            if (hasParam) {
                sb.append("}");
                return sb.toString();
            }
        }

        // 回退：从 request 获取
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            Map<String, String[]> paramMap = request.getParameterMap();
            if (paramMap != null && !paramMap.isEmpty()) {
                return JSON.toJSONString(paramMap);
            }
        }
        return "";
    }

    /**
     * 判断是否为关键 Header（只输出这些）
     */
    private boolean isKeyHeader(String name) {
        for (String key : KEY_HEADERS) {
            if (key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 格式化 JSON body（单行 JSON 转为格式化输出）
     */
    private String formatJsonBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        try {
            Object obj = JSON.parse(body);
            String formatted = JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue);
            // 将制表符替换为 2 个空格，避免右边框错位
            return formatted.replace("\t", "  ");
        } catch (Exception e) {
            // 不是合法 JSON，原样返回
            return body;
        }
    }

    /**
     * 判断 URL 是否在排除列表中
     */
    private boolean isExcluded(String uri) {
        if (properties == null || properties.getExcludeUrls() == null) {
            return false;
        }
        for (String pattern : properties.getExcludeUrls()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 追加一行到 StringBuilder
     */
    private void appendLine(StringBuilder sb, String line) {
        sb.append(line).append(System.lineSeparator());
    }
}
