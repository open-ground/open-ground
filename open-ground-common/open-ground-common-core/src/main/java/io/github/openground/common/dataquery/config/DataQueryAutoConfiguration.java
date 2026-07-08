package io.github.openground.common.dataquery.config;

import org.springframework.context.annotation.ComponentScan;

/**
 * 数据查询导出自动配置
 *
 * <p>无条件扫描 dataquery 包，注册 Controller 和 Service。
 *
 * @author open-ground
 * @since 1.0.2
 */
@ComponentScan(basePackages = "io.github.openground.common.dataquery")
public class DataQueryAutoConfiguration {
}
