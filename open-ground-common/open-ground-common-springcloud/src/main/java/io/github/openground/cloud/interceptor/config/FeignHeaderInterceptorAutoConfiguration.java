package io.github.openground.cloud.interceptor.config;

import io.github.openground.cloud.interceptor.FeignHeaderInterceptor;
import io.github.openground.common.config.condition.ConditionalOnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * FeignHeaderInterceptor 自动配置（Service 部署模式）
 *
 * <p>注册 {@link FeignHeaderInterceptor} 为 Feign 请求拦截器，
 * 自动向所有 Feign 请求注入 authorization Token、Menu-ID 等请求头。</p>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
@ConditionalOnService
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
public class FeignHeaderInterceptorAutoConfiguration {

    /**
     * Feign 请求拦截器
     * <p>自动注入 authorization Token、encrypt 标记、Menu-ID 等请求头。</p>
     */
    @Bean
    @ConditionalOnMissingBean(FeignHeaderInterceptor.class)
    public FeignHeaderInterceptor feignHeaderInterceptor() {
        log.info("注册 FeignHeaderInterceptor（Service 模式，自动注入请求头）");
        return new FeignHeaderInterceptor();
    }
}