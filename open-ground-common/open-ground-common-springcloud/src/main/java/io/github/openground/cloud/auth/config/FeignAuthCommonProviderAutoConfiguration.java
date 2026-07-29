package io.github.openground.cloud.auth.config;

import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.cloud.auth.FeignAuthCommonProvider;
import io.github.openground.common.auth.AuthCommonProvider;
import io.github.openground.common.config.condition.ConditionalOnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FeignAuthCommonProvider 自动配置（Service 部署模式）
 *
 * <p>当 {@code ground.mode=service} 时生效，通过 Feign 远程调用 ground-auth 服务
 * 实现 {@link AuthCommonProvider} 接口。</p>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
@Configuration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
public class FeignAuthCommonProviderAutoConfiguration {

    /**
     * Feign 远程调用实现的 AuthCommonProvider
     * <p>当容器中未注入其他 {@link AuthCommonProvider} Bean 时自动装配。</p>
     */
    @Bean
    @ConditionalOnMissingBean(AuthCommonProvider.class)
    public AuthCommonProvider feignAuthCommonProvider(AuthFeignClient authFeignClient) {
        log.info("初始化 FeignAuthCommonProvider（Service 模式，远程调用 Auth 服务）");
        return new FeignAuthCommonProvider(authFeignClient);
    }
}