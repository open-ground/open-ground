package io.github.openground.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Open Ground 测试应用启动类
 * <p>模拟使用 open-ground 框架的业务项目，通过 @ComponentScan 扫描框架所有组件，
 * @EnableOptLog 启用操作日志切面，@MapperScan 注册 MyBatis Mapper。</p>
 *
 * @author open-ground
 */
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setAllowBeanDefinitionOverriding(true);
        application.setAllowCircularReferences(true);
        application.run(args);
    }
}
