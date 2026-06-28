package io.github.openground.common.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 请求/响应日志配置属性
 *
 * <p>配置前缀：{@code ground.log.request-log}</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "ground.log.request-log")
public class RequestLogProperties {

    /** 是否启用请求/响应日志，默认关闭 */
    private boolean enabled = false;

    /** Body 最大字节数，超过则截断，默认 10240（10KB） */
    private int maxBodyLength = 10240;

    /**
     * 扫描的 Controller 包路径前缀列表
     * <p>为空时拦截所有 {@code @Controller} / {@code @RestController}，非空时只拦截指定包下的 controller。</p>
     */
    private List<String> scanPackages = new ArrayList<>();

    /**
     * 是否配置了扫描包限制
     *
     * @return true 表示需要按包过滤
     */
    public boolean hasScanPackages() {
        return scanPackages != null && !scanPackages.isEmpty();
    }

    /** 排除的 URL 模式（Ant 路径模式），如 /swagger-ui/** */
    private List<String> excludeUrls = new ArrayList<>();

    // ========== 敏感信息脱敏配置 ==========

    /**
     * 敏感 Header 名称列表（不区分大小写）
     * <p>匹配的 Header 值将被截断显示。</p>
     */
    private List<String> sensitiveHeaders = new ArrayList<>(Arrays.asList(
            "authorization", "token", "api-key", "api_key", "app-key", "app_secret"
    ));

    /**
     * 敏感 JSON 字段名列表（不区分大小写）
     * <p>匹配的 JSON 字段值将被替换为 {@link #maskText}。</p>
     */
    private List<String> sensitiveFields = new ArrayList<>(Arrays.asList(
            "password", "secret", "oldPwd", "newPwd", "oldPassword", "newPassword",
            "pwd", "token", "apiKey", "api_key", "accessKey", "accessSecret",
            "appKey", "appSecret", "refreshToken", "idToken"
    ));

    /**
     * 敏感 Header 值预览长度
     * <p>前 N 个字符保留显示，超出部分替换为 {@code ***}。</p>
     */
    private int headerPreviewLength = 30;

    /**
     * 敏感字段掩码文本
     * <p>替换敏感字段值的显示文本。</p>
     */
    private String maskText = "******";
}
