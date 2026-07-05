package io.github.openground.common.dbcheck.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * DbCheck 组件扫描配置（无条件）
 *
 * <p>与 {@link DbCheckAutoConfiguration} 分离的原因是：
 * Spring Boot 3.4.x（Spring Framework 6.2.x）不允许
 * {@code @ComponentScan} / {@code @MapperScan}（PARSING 阶段）
 * 与 {@code @ConditionalOnBean}（REGISTER_BEAN 阶段）在同一配置类上共存。</p>
 *
 * <p>本类只负责扫描注册 {@code @Component} / {@code @Service} / {@code @Mapper}，
 * 条件装配（DataSource 存在 + db-check.enabled）交由 {@link DbCheckAutoConfiguration} 控制。</p>
 *
 * @author open-ground
 * @since 2026-07-05
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.github.openground.common.dbcheck")
@MapperScan(basePackages = "io.github.openground.common.dbcheck.dao")
public class DbCheckCoreAutoConfiguration {

}
