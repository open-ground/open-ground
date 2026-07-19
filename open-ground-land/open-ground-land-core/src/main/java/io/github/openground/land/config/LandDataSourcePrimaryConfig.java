package io.github.openground.land.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 业务数据源 @Primary 标记配置
 * <p>
 * Land 框架引入 {@link LandDataSourceConfig} 后，容器中存在两个 DataSource：
 * <ul>
 *   <li>{@code dataSource} — 业务数据源（{@code spring.datasource}），由 Druid / Hikari 自动配置创建</li>
 *   <li>{@code landDataSource} — Land 框架自有数据源（{@code ground.land.datasource}），用于调度表</li>
 * </ul>
 * 两者均未标注 {@code @Primary}，导致 {@code JdbcTemplateAutoConfiguration}
 * 因 {@code @ConditionalOnSingleCandidate(DataSource.class)} 不匹配而跳过。
 * </p>
 * <p>
 * 本配置将业务 {@code dataSource} 包装为 {@code @Primary} Bean，
 * 使 {@code JdbcTemplate} 使用业务数据源。Land 框架的 Mapper 仍通过
 * {@code landSqlSessionFactory} 显式绑定 {@code landDataSource}，不受影响。
 * </p>
 *
 * @author open-ground
 * @since 1.0.7
 */
@Slf4j
@AutoConfiguration
@ConditionalOnBean(name = "dataSource")
@org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(io.github.openground.common.jdbc.RoutingDataSource.class)
public class LandDataSourcePrimaryConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource(@Qualifier("dataSource") DataSource original) {
        log.info("标记 dataSource 为 @Primary（业务主数据源）");
        return original;
    }
}
