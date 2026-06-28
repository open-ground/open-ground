package io.github.openground.common.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志组件配置属性
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "ground.log")
public class OptLogProperties {

    /** 是否启用操作日志组件，默认启用 */
    private boolean enabled = true;

    /** 日志参数最大字节数，默认 2000 */
    private int logLength = 2000;

    /**
     * 序列化请求参数时是否过滤 null 值
     * <p>同时控制 {@code RequestLogAspect} 和 {@code OptLogAspect} 的参数输出。
     * {@code true} 时跳过 null 字段，{@code false} 时保留 null 字段。</p>
     */
    private boolean filterNullParams = true;
}
