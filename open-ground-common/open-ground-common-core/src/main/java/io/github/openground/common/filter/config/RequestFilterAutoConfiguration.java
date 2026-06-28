package io.github.openground.common.filter.config;

import io.github.openground.common.config.condition.ConditionalOnAuth;
import io.github.openground.common.filter.CommonRequestFilter;
import io.github.openground.common.filter.TokenCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * CommonRequestFilter 自动配置（Auth 模式）
 *
 * <p>仅在 {@code ground.mode=auth}（默认值）时生效。</p>
 * <p>Auth 模式下 Token 校验由 auth-core 自身处理，此过滤器的 tokenCheckEnabled 请保持 {@code false}。</p>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
@ConditionalOnAuth
@ConditionalOnProperty(prefix = "ground.security.request-filter", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RequestFilterProperties.class)
public class RequestFilterAutoConfiguration {

    /**
     * 注册 CommonRequestFilter
     *
     * @param properties 过滤器配置属性
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean(CommonRequestFilter.class)
    public FilterRegistrationBean<CommonRequestFilter> registRequestFilter(
            RequestFilterProperties properties,
            ObjectProvider<TokenCheckService> tokenCheckServiceProvider,
            Environment environment) {
        log.info("注册 CommonRequestFilter，order={}, tokenCheckEnabled={}, decryptEnabled={}, urlRegularEnabled={}",
                properties.getOrder(), properties.isTokenCheckEnabled(), properties.isDecryptEnabled(), properties.isUrlRegularEnabled());

        CommonRequestFilter filter = new CommonRequestFilter(properties);
        filter.setEnvironment(environment);
        tokenCheckServiceProvider.ifAvailable(filter::setTokenCheckService);

        FilterRegistrationBean<CommonRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("CommonRequestFilter");
        registration.setOrder(properties.getOrder());
        return registration;
    }
}
