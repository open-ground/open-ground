package io.github.openground.cloud.keygen.config;

import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.cloud.keygen.FeignSequenceProvider;
import io.github.openground.common.config.condition.ConditionalOnService;
import io.github.openground.common.keygen.SequenceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 序列提供者自动配置（分离部署模式）
 * <p>
 * 当 classpath 中存在 FeignClient 且部署模式为 {@code separated} 时自动生效：
 * <ul>
 *   <li>SequenceProvider → {@link FeignSequenceProvider}（远程调用 Auth 获取序列）</li>
 * </ul>
 * </p>
 *
 * @author open-ground
 */
@Slf4j
@Configuration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@EnableFeignClients(basePackages = "io.github.openground.cloud.auth")
public class FeignKeyGenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SequenceProvider.class)
    public SequenceProvider feignSequenceProvider(AuthFeignClient feignClient) {
        log.info("Feign 序列提供者已启用（远程调用 Auth 获取序列）");
        return new FeignSequenceProvider(feignClient);
    }
}
