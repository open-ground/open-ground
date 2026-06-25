package io.github.openground.land.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Land 核心业务自动装配
 * <p>扫描 core 模块下的 Service、Job 等组件，使其被 Spring 容器管理。</p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@AutoConfiguration
@ComponentScan("io.github.openground.land")
public class LandCoreAutoConfiguration {

}
