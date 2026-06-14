package io.github.openground.cloud.auth.config;

import io.github.openground.cloud.auth.SessionFeignClient;
import io.github.openground.cloud.auth.FeignTokenStore;
import io.github.openground.common.config.condition.ConditionalOnService;
import io.github.openground.common.security.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign TokenStore 自动配置（分离部署模式）
 * <p>
 * 当部署模式为 {@code service} 且 classpath 中存在 FeignClient 时自动生效：
 * <ul>
 *   <li>TokenStore → {@link FeignTokenStore}（远程调用 Auth 操作会话）</li>
 * </ul>
 * </p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Configuration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@EnableFeignClients(basePackages = "io.github.openground.cloud.auth")
public class FeignTokenStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore feignTokenStore(SessionFeignClient sessionFeignClient) {
        log.info("FeignTokenStore 已启用（远程调用 Auth 操作会话数据）");
        return new FeignTokenStore(sessionFeignClient);
    }
}
