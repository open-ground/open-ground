package io.github.openground.cloud.log.config;

import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.cloud.log.FeignLogSender;
import io.github.openground.common.config.condition.ConditionalOnService;
import io.github.openground.common.log.service.LogSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 操作日志自动配置（分离部署模式）
 * <p>
 * 当 classpath 中存在 FeignClient 且部署模式为 {@code separated} 时自动生效：
 * <ul>
 *   <li>LogSender → {@link FeignLogSender}（远程调用 Auth 保存日志）</li>
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
public class FeignLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogSender.class)
    public LogSender feignLogSender(AuthFeignClient feignClient) {
        log.info("Feign 操作日志发送器已启用（远程调用 Auth 保存日志）");
        return new FeignLogSender(feignClient);
    }
}
