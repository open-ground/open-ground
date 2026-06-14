package io.github.openground.common.log.config;

import io.github.openground.common.config.condition.ConditionalOnAuth;
import io.github.openground.common.log.aspect.OptLogAspect;
import io.github.openground.common.log.listener.OptLogEventListener;
import io.github.openground.common.log.local.JdbcLogSender;
import io.github.openground.common.log.service.LogSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 操作日志组件自动配置
 * <p>
 * <b>设计原则：通过 SPI 接口 {@link LogSender} 解耦日志存储方式。</b>
 * <ul>
 *   <li>集成部署模式 → 默认提供 {@link JdbcLogSender}（JdbcTemplate 直写 {@code sys_opt_log} 表）</li>
 *   <li>分离部署模式 → 由 {@code open-ground-common-springcloud} 模块提供 Feign 远程实现</li>
 * </ul>
 * </p>
 * <p>
 * 业务模块通过 {@link EnableOptLog} 开启后，OptLogAspect 自动生效。
 * 切面收集日志数据后发布 {@code OptLogEvent}，由 {@code OptLogEventListener} 异步消费。
 * LogSender 通过 {@code @ConditionalOnMissingBean} 装配，其他模块可替换默认实现。
 * </p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Configuration
@ConditionalOnAuth
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(OptLogProperties.class)
@ConditionalOnProperty(prefix = "ground.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
public class OptLogAutoConfiguration {

    /**
     * 操作日志切面
     * <p>收集日志数据并通过 Spring 事件发布，由异步监听器消费。</p>
     */
    @Bean
    public OptLogAspect optLogAspect(OptLogProperties properties) {
        OptLogAspect aspect = new OptLogAspect();
        aspect.setLogLength(properties.getLogLength());
        return aspect;
    }

    /**
     * 操作日志事件监听器
     * <p>异步监听 {@code OptLogEvent}，通过 SPI {@code LogSender} 发送操作日志。</p>
     */
    @Bean
    public OptLogEventListener optLogEventListener() {
        return new OptLogEventListener();
    }

    /**
     * 默认 JdbcLogSender（集成部署模式）
     * <p>当容器中未注入其他 {@link LogSender} Bean 时自动装配。</p>
     */
    @Bean
    @ConditionalOnMissingBean(LogSender.class)
    public LogSender jdbcLogSender(JdbcTemplate jdbcTemplate) {
        log.info("初始化 JdbcLogSender（集成部署模式，直写 sys_opt_log 表）");
        return new JdbcLogSender(jdbcTemplate);
    }
}
