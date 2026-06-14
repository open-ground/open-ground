package io.github.openground.test;

import io.github.openground.common.log.config.EnableOptLog;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Open Ground 测试应用启动类
 * <p>模拟使用 open-ground 框架的业务项目，通过 @ComponentScan 扫描框架所有组件，
 * @EnableOptLog 启用操作日志切面，@MapperScan 注册 MyBatis Mapper。</p>
 *
 * @author open-ground
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.github.openground", "io.github.openground.test"})
@MapperScan("io.github.openground.common.security.mapper")
@EnableOptLog
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
