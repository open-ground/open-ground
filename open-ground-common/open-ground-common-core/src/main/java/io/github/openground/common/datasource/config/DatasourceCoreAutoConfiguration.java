package io.github.openground.common.datasource.config;

import io.github.openground.common.config.condition.ConditionalOnAuth;
import io.github.openground.common.datasource.SysDatasourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据源管理组件扫描配置(集成部署模式)
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
@Slf4j
@ConditionalOnAuth
@AutoConfiguration
@ComponentScan(basePackages = "io.github.openground.common.datasource")
@MapperScan(basePackages = "io.github.openground.common.datasource.mapper")
public class DatasourceCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SysDatasourceProvider.class)
    public SysDatasourceProvider sysDatasourceProvider() {
        log.info("初始化 SysDatasourceProvider（本地直连 Auth 获取数据源）");
        return new SysDatasourceProvider();
    }
}
