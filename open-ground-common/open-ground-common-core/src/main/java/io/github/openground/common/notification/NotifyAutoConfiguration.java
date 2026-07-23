package io.github.openground.common.notification;

import io.github.openground.common.notification.spi.NotifyChannel;
import io.github.openground.common.notification.spi.NotifyMessageStore;
import io.github.openground.common.notification.spi.NotifyTemplateRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 通知组件 Spring Boot 自动配置
 *
 * <p>自动装配：
 * <ul>
 *   <li>{@link NotifyTemplateRenderer} — 默认使用 {@link DefaultNotifyTemplateRenderer}</li>
 *   <li>{@link Notifier} — 使用 {@link NotifierImpl}</li>
 *   <li>{@code notifyExecutor} — 异步发送线程池</li>
 * </ul>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyAutoConfiguration {

    /**
     * 默认模板渲染器
     * 业务方可通过实现 {@link NotifyTemplateRenderer} 并注册为 Bean 替换
     */
    @Bean
    @ConditionalOnMissingBean(NotifyTemplateRenderer.class)
    public NotifyTemplateRenderer notifyTemplateRenderer() {
        return new DefaultNotifyTemplateRenderer();
    }

    /**
     * 通知器
     */
    @Bean
    @ConditionalOnMissingBean(Notifier.class)
    public Notifier notifier(List<NotifyChannel> channels,
                             NotifyTemplateRenderer renderer) {
        return new NotifierImpl(channels, renderer);
    }

    /**
     * 站内信通知渠道
     * <p>默认启用，可通过 {@code ground.notify.internal-enabled=false} 关闭。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ground.notify", name = "internal-enabled",
            havingValue = "true", matchIfMissing = true)
    public InternalNotifyChannel internalNotifyChannel(ObjectProvider<NotifyMessageStore> messageStoreProvider) {
        return new InternalNotifyChannel(messageStoreProvider.getIfAvailable());
    }

    /**
     * 异步通知发送线程池
     */
    @Bean("notifyExecutor")
    public Executor notifyExecutor(NotifyProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler((r, e) -> {
            logRejected(r);
            throw new RuntimeException("通知线程池已满，拒绝任务");
        });
        executor.initialize();
        return executor;
    }

    private void logRejected(Runnable r) {
        if (r instanceof org.springframework.scheduling.annotation.AsyncResult) {
            // 异步任务被拒绝时记录日志
        }
    }
}
