package io.github.openground.land.dmp.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据管理平台自动装配
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@AutoConfiguration
@ComponentScan("io.github.openground.land.dmp")
@MapperScan(basePackages = "io.github.openground.land.dmp.mapper",
            sqlSessionFactoryRef = "landSqlSessionFactory")
public class DmpAutoConfiguration {
}
