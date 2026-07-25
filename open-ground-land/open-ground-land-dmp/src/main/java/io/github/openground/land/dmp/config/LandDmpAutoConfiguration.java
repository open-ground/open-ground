package io.github.openground.land.dmp.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据管理平台自动装配
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@AutoConfiguration
@ComponentScan("io.github.openground.land.dmp")
@MapperScan(basePackages = "io.github.openground.land.dmp.mapper",sqlSessionFactoryRef = "landSqlSessionFactory")
public class LandDmpAutoConfiguration {
}

