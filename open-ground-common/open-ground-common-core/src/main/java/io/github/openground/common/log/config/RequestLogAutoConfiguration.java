package io.github.openground.common.log.config;

import io.github.openground.common.log.aspect.RequestLogAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 请求/响应日志自动配置
 *
 * <p>通过 {@code ground.log.request-log.enabled=true} 启用。</p>
 * <p>启用后，所有 Controller 方法的请求输入和响应输出将以 debug 级别打印到日志中，
 * 格式为 Unicode 边框，便于开发调试。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(RequestLogProperties.class)
@ConditionalOnProperty(prefix = "ground.log.request-log", name = "enabled", havingValue = "true")
public class RequestLogAutoConfiguration {

    /**
     * 请求/响应日志切面
     * <p>拦截所有 Controller 方法，以 debug 级别输出请求参数和响应结果。</p>
     *
     * @param properties 请求日志配置属性
     * @return RequestLogAspect 实例
     */
    @Bean
    public RequestLogAspect requestLogAspect(RequestLogProperties properties) {
        log.info("初始化 RequestLogAspect（请求/响应日志），maxBodyLength={}, excludeUrls={}",
                properties.getMaxBodyLength(), properties.getExcludeUrls());
        RequestLogAspect aspect = new RequestLogAspect();
        aspect.setProperties(properties);
        return aspect;
    }
}
