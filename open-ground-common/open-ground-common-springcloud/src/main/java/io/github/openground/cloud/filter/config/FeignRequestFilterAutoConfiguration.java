package io.github.openground.cloud.filter.config;

import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.cloud.filter.FeignTokenCheckService;
import io.github.openground.common.config.condition.ConditionalOnService;
import io.github.openground.common.filter.CommonRequestFilter;
import io.github.openground.common.filter.config.RequestFilterProperties;
import io.github.openground.common.filter.TokenCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * CommonRequestFilter 自动配置（Service 模式）
 *
 * <p>仅在 {@code ground.mode=service} 时生效。</p>
 * <p>Service 模式下自动装配 {@link FeignTokenCheckService} 进行远程 Token 校验，
 * 同时提供 {@link CommonRequestFilter} 过滤器用于解密、验签、URL 检查等功能。</p>
 *
 * @author open-ground
 */
@Slf4j
@Configuration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@ConditionalOnProperty(prefix = "ground.security.request-filter", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RequestFilterProperties.class)
@EnableFeignClients(basePackages = "io.github.openground.cloud.auth")
public class FeignRequestFilterAutoConfiguration {

    /**
     * Feign Token 校验服务
     */
    @Bean
    @ConditionalOnMissingBean(TokenCheckService.class)
    public TokenCheckService feignTokenCheckService(AuthFeignClient feignClient) {
        log.info("Feign Token 校验服务已启用（远程调用 Auth 校验 Token）");
        return new FeignTokenCheckService(feignClient);
    }

    /**
     * 注册 CommonRequestFilter
     */
    @Bean
    @ConditionalOnMissingBean(CommonRequestFilter.class)
    public FilterRegistrationBean<CommonRequestFilter> registRequestFilter(
            RequestFilterProperties properties,
            TokenCheckService tokenCheckService,
            Environment environment) {
        log.info("注册 CommonRequestFilter（Service 模式），order={}, tokenCheckEnabled={}, decryptEnabled={}, urlRegularEnabled={}",
                properties.getOrder(), properties.isTokenCheckEnabled(), properties.isDecryptEnabled(), properties.isUrlRegularEnabled());

        CommonRequestFilter filter = new CommonRequestFilter(properties);
        filter.setEnvironment(environment);
        filter.setTokenCheckService(tokenCheckService);

        FilterRegistrationBean<CommonRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("CommonRequestFilter");
        registration.setOrder(properties.getOrder());
        return registration;
    }
}
