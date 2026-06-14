package io.github.openground.common.security.config;

import io.github.openground.common.security.TokenManager;
import io.github.openground.common.security.filter.AuthTokenManagerFilter;
import io.github.openground.common.security.filter.DefaultTokenExtractor;
import io.github.openground.common.security.filter.TokenExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Token 鉴权过滤器自动配置
 *
 * <p>仅在 Web 应用（非 reactive）且 {@code ground.security.token-filter.enabled=true}（默认）时生效。</p>
 * <p>业务模块可通过如下方式禁用：</p>
 * <pre>
 *   ground.security.token-filter.enabled=false
 * </pre>
 * <p>业务模块可通过自定义 {@link TokenExtractor} Bean 覆盖默认的 Token 提取策略。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FilterRegistrationBean.class)
@ConditionalOnProperty(prefix = "ground.security.token-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TokenFilterProperties.class)
public class TokenFilterAutoConfiguration {

    /**
     * 默认 Token 提取器
     * <p>从 {@code Authorization: Bearer xxx} header 中提取 Token。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TokenExtractor.class)
    public TokenExtractor tokenExtractor() {
        log.debug("初始化 DefaultTokenExtractor");
        return new DefaultTokenExtractor();
    }

    /**
     * 注册 Token 鉴权过滤器
     */
    @Bean
    public FilterRegistrationBean<AuthTokenManagerFilter> authTokenFilter(
            TokenManager tokenManager,
            TokenExtractor tokenExtractor,
            TokenFilterProperties properties) {

        AuthTokenManagerFilter filter = new AuthTokenManagerFilter(tokenManager, tokenExtractor);
        filter.setWhiteList(properties.getWhiteList());
        filter.setEnabled(properties.isEnabled());

        FilterRegistrationBean<AuthTokenManagerFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setUrlPatterns(properties.getUrlPatterns());
        registration.setName("authTokenFilter");
        registration.setOrder(properties.getOrder());

        log.info("注册 AuthTokenFilter: enabled={}, order={}, whiteList={}",
                properties.isEnabled(), properties.getOrder(), properties.getWhiteList());

        return registration;
    }
}
