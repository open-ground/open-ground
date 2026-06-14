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
}
