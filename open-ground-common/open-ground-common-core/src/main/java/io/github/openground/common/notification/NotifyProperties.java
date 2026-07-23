package io.github.openground.common.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知组件配置属性
 *
 * <p>配置前缀：{@code ground.notify}
 *
 * @author open-ground
 * @since 1.0.6
 */
@Data
@ConfigurationProperties(prefix = "ground.notify")
public class NotifyProperties {

    /** 站内信渠道开关，默认启用 */
    private boolean internalEnabled = true;

    /** 异步线程池配置 */
    private Async async = new Async();

    @Data
    public static class Async {

        /** 核心线程数 */
        private int corePoolSize = 4;

        /** 最大线程数 */
        private int maxPoolSize = 8;

        /** 队列容量 */
        private int queueCapacity = 100;
    }
}
