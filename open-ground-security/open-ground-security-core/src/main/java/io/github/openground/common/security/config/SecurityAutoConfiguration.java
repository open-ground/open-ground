package io.github.openground.common.security.config;

import io.github.openground.common.security.AuthProperties;
import io.github.openground.common.security.impl.ApiKeyServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Security 自动配置
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@ComponentScan(basePackages = "io.github.openground.common.security")
@MapperScan("io.github.openground.common.security.mapper")
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyServiceImpl apiKeyService() {
        log.info("初始化 ApiKeyService");
        return new ApiKeyServiceImpl();
    }
}