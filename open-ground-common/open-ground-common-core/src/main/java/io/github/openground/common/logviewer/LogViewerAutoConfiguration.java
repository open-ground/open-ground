package io.github.openground.common.logviewer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

/**
 * 日志查看器自动配置
 *
 * <p>扫描 {@code io.github.openground.common.logviewer} 包下的所有组件
 * （LogController、LogService、LogNodeProxyService 等），使其在宿主应用的
 * {@code @ComponentScan} 未覆盖该包路径时仍能被注册为 Spring Bean。
 *
 * @author open-ground
 */
@AutoConfiguration
@ComponentScan("io.github.openground.common.logviewer")
public class LogViewerAutoConfiguration {

    /**
     * 默认 RestTemplate，用于跨节点日志代理请求
     * <p>下游应用可通过自定义 {@code @Bean} 覆盖此默认实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
