package io.github.openground.common.datasource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据源管理组件扫描配置（无条件）
 *
 * <p>负责扫描 {@code io.github.openground.common.datasource} 包下的
 * {@code @Component} / {@code @Service} / {@code @RestController} / {@code @Mapper}，
 * 使 {@link io.github.openground.common.datasource.service.SysDatasourceService}、
 * {@link io.github.openground.common.datasource.SysDatasourceProvider}、
 * {@link io.github.openground.common.datasource.controller.SysDatasourceController}
 * 等组件自动注册为 Spring Bean。</p>
 *
 * @author open-ground
 * @since 2026-07-05
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.github.openground.common.datasource")
@MapperScan(basePackages = "io.github.openground.common.datasource.mapper")
public class DatasourceCoreAutoConfiguration {

}
