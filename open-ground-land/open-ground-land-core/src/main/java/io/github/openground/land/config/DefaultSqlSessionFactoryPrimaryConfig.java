package io.github.openground.land.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Land 多数据源场景下的 SqlSessionFactory / SqlSessionTemplate @Primary 配置
 * <p>
 * Land 框架引入了独立的框架数据源（ground.land.datasource），创建了第二个
 * SqlSessionFactory 和 SqlSessionTemplate。当业务项目同时有默认数据源时，
 * MyBatis Plus 的默认工厂和模板不再自动成为 @Primary，
 * 导致未指定 sqlSessionFactoryRef 的 @MapperScan（如 security 模块）无法注入。
 * </p>
 * <p>
 * 本配置将默认的 sqlSessionFactory 和 sqlSessionTemplate 重新标记为 @Primary，
 * 确保业务模块的 Mapper 正常使用默认数据源，
 * 而 Land 框架 Mapper 通过 @MapperScan(sqlSessionFactoryRef = "landSqlSessionFactory") 绑定框架数据源。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@AutoConfiguration
@ConditionalOnBean(name = "sqlSessionFactory")
public class DefaultSqlSessionFactoryPrimaryConfig {

    @Bean
    @Primary
    public SqlSessionFactory primaryDefaultSqlSessionFactory(
            @Qualifier("sqlSessionFactory") SqlSessionFactory defaultFactory) {
        return defaultFactory;
    }

    @Bean
    @Primary
    public SqlSessionTemplate primaryDefaultSqlSessionTemplate(
            @Qualifier("sqlSessionTemplate") SqlSessionTemplate defaultTemplate) {
        return defaultTemplate;
    }
}
