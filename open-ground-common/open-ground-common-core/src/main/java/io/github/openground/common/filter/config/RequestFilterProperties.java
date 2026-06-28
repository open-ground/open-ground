package io.github.openground.common.filter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CommonRequestFilter 配置属性
 *
 * <p>配置前缀：{@code ground.security.request-filter}</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "ground.security.request-filter")
public class RequestFilterProperties {

    /**
     * 是否启用 CommonRequestFilter
     */
    private boolean enabled = false;

    /**
     * 是否启用 URL 非法字符检查（SQL 注入拦截等）
     */
    private boolean urlRegularEnabled = false;

    /**
     * 是否启用请求体解密和验签
     */
    private boolean decryptEnabled = false;

    /**
     * 加密算法，支持 AES / SM4
     */
    private String algorithm = "AES";

    /**
     * 加密密钥
     */
    private String encryptKey = "ABCDEFG123456KEY";

    /**
     * 加密偏移量（AES 算法时可为空）
     */
    private String encryptIv = "ABCDEFG1234567IV";

    /**
     * 签名过期时间（毫秒），默认 5 分钟
     */
    private long signExpire = 300000;

    /**
     * 是否启用 Token 校验
     * <p>启用后需要注入 {@link io.github.openground.common.filter.TokenCheckService} 的实现 Bean。</p>
     */
    private boolean tokenCheckEnabled = false;

    /**
     * 白名单 URL 模式列表（Ant 路径模式）
     * <p>匹配的 URL 将跳过所有检查（Token 校验、解密验签、URL 检查等）。</p>
     */
    private List<String> whiteList = new ArrayList<>();

    /**
     * Filter 排序号，用于 FilterRegistrationBean
     */
    private int order = 1;
}
