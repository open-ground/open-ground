package io.github.openground.land.dispatch.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Land 调度中心自动装配
 * <p>扫描 dispatch 模块下的 Controller、RemoteTaskExecutor、DbServiceDiscovery 等组件，
 * 使其被 Spring 容器管理并可以被 SpringDoc OpenAPI 扫描到。</p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@AutoConfiguration
@ComponentScan("io.github.openground.land.dispatch")
public class LandDispatchAutoConfiguration {

}
