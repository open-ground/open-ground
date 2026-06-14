package io.github.openground.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.List;

/**
 * Token 鉴权过滤器配置属性
 *
 * <p>配置前缀：{@code ground.security.token-filter}</p>
 *
 * <pre class="code">
 *   ground.security.token-filter.enabled=true
 *   ground.security.token-filter.white-list[0]=/swagger-ui/**
 *   ground.security.token-filter.order=-100
 * </pre>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "ground.security.token-filter")
public class TokenFilterProperties {

    /** 是否启用 Token 鉴权过滤器，默认启用 */
    private boolean enabled = true;

    /** 白名单路径（Ant 路径模式），这些路径不需要 Token 校验 */
    private List<String> whiteList = Arrays.asList(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error",
            "/favicon.ico"
    );

    /** 过滤器执行顺序，默认最高优先级 +10 */
    private int order = Ordered.HIGHEST_PRECEDENCE + 10;

    /** 拦截的 URL 路径，默认拦截所有 */
    private List<String> urlPatterns = Arrays.asList("/*");
}
