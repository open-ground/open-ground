package io.github.openground.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 认证配置属性
 * <p>配置前缀：ground.security</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ground.security")
public class AuthProperties {

    /** Token 存储方式：db 或 redis */
    private String tokenStore = "db";

    /** Token 超时时间（分钟） */
    private Integer tokenTimeout = 1440;

    /** 是否允许多设备登录 */
    private Boolean multiLogin = true;

    /** Token 刷新黑名单路径 */
    private List<String> refreshBlacklist;

    /** API Key 配置 */
    private ApiKeyProperties apiKey = new ApiKeyProperties();

    @Data
    public static class ApiKeyProperties {
        /** 是否启用 API Key 功能 */
        private Boolean enabled = true;

        /** API Key 默认过期天数 */
        private Integer expireDays = 30;
    }
}