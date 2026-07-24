package io.github.openground.common.jdbc.config;

import io.github.openground.common.jdbc.ConfigDataSourceProvider;
import io.github.openground.common.jdbc.DataSourceProvider;
import io.github.openground.common.jdbc.DataSourceProviderRegistry;
import io.github.openground.common.jdbc.DynamicDataSourceManager;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.common.jdbc.DynamicSqlSessionFactoryManager;
import io.github.openground.common.jdbc.RoutingDataSource;
import io.github.openground.common.jdbc.dialect.DbDialectRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;
import java.util.List;

/**
 * 多数据源动态 JDBC 自动配置
 *
 * <p>提供：
 * <ul>
 *   <li>{@link DruidProperties} — Druid 连接池配置</li>
 *   <li>{@link DynamicDataSourceProperties} — 多数据源列表配置</li>
 *   <li>{@link ConfigDataSourceProvider} — dblist 配置式数据源提供者</li>
 *   <li>{@link DataSourceProviderRegistry} — 数据源提供者注册中心</li>
 *   <li>{@link DynamicDataSourceManager} — Druid 连接池管理器</li>
 *   <li>{@link DynamicJdbcTemplate} — 动态 JDBC 模板</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
// 必须在 MybatisPlusAutoConfiguration 之前加载，确保 RoutingDataSource（@Primary）
// 先于 MybatisPlus 主 SqlSessionFactory 创建，否则 MybatisPlus 因多个 DataSource 无 @Primary 而报错。
// MybatisPlus 3.x 的自动配置类名稳定，若未来升级大版本需同步更新此类名。
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@EnableConfigurationProperties({DynamicDataSourceProperties.class, DruidProperties.class})
@ComponentScan(basePackages = "io.github.openground.common.jdbc.dialect")
public class DynamicDataSourceAutoConfiguration {

    /**
     * Druid 连接池配置
     */
    @Bean
    @ConditionalOnMissingBean
    public DruidProperties druidProperties() {
        return new DruidProperties();
    }

    /**
     * 配置式数据源提供者（从 ground.dblist 读取）
     */
    @Bean
    @ConditionalOnMissingBean(ConfigDataSourceProvider.class)
    public ConfigDataSourceProvider configDataSourceProvider(DynamicDataSourceProperties properties) {
        return new ConfigDataSourceProvider(properties);
    }

    /**
     * 数据源提供者注册中心
     *
     * <p>收集所有 {@link DataSourceProvider} Bean（含 {@link ConfigDataSourceProvider}
     * 和各模块自定义的 Provider），允许空列表。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSourceProviderRegistry dataSourceProviderRegistry(
            List<DataSourceProvider> providers) {
        return new DataSourceProviderRegistry(providers);
    }

    /**
     * 数据库方言注册中心
     *
     * <p>自动收集所有 {@link io.github.openground.common.jdbc.dialect.DbDialect} 实现。
     * 作为 {@code @Bean} 显式定义，不依赖外部 {@code @ComponentScan}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public DbDialectRegistry dbDialectRegistry(
            List<io.github.openground.common.jdbc.dialect.DbDialect> dialects) {
        return new DbDialectRegistry(dialects);
    }

    /**
     * 多数据源管理器（Druid + Provider 模式）
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicDataSourceManager dynamicDataSourceManager(
            DataSourceProviderRegistry registry,
            DruidProperties druidProperties,
            List<DataSource> dataSources) {
        // 过滤掉 RoutingDataSource 自身，避免 primaryDataSource 指向 RoutingDataSource 形成递归
        DataSource primary = dataSources.stream()
                .filter(ds -> !(ds instanceof RoutingDataSource))
                .findFirst()
                .orElse(null);
        log.info("动态数据源管理器已启用（Druid 连接池），共 {} 个动态数据源: {}",
                registry.getDataSourceNames().size(),
                registry.getDataSourceNames().isEmpty() ? "无" : registry.getDataSourceNames());
        return new DynamicDataSourceManager(registry, druidProperties, primary);
    }

    /**
     * 路由数据源 - MyBatis 动态多数据源的核心
     *
     * <p>作为 {@code @Primary} 数据源，MyBatis-Plus 主 SqlSessionFactory 和 JdbcTemplate
     * 都会持有它。未 push dsName 时回退到默认数据源（Spring 主数据源），
     * push 后路由到指定动态数据源。
     *
     * @param dataSourceManager 动态数据源管理器
     * @param primaryDataSource Spring 主数据源（作为默认回退）
     * @return 路由数据源
     */
    @Bean
    @org.springframework.context.annotation.Primary
    @ConditionalOnMissingBean(RoutingDataSource.class)
    public RoutingDataSource routingDataSource(@org.springframework.beans.factory.annotation.Qualifier("dataSource") DataSource primaryDataSource) {
        RoutingDataSource routingDs = new RoutingDataSource();
        routingDs.setDefaultTargetDataSource(primaryDataSource);
        log.info("路由数据源已启用（@Primary），默认回退: Spring 主数据源 (dataSource)");
        return routingDs;
    }

    /**
     * 动态数据源 SqlSessionFactory 管理器（复用主 SqlSessionFactory 配置）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    public DynamicSqlSessionFactoryManager dynamicSqlSessionFactoryManager(
            DynamicDataSourceManager dataSourceManager,
            SqlSessionFactory primarySqlSessionFactory,
            RoutingDataSource routingDataSource) {
        // 延迟注入 DynamicDataSourceManager 到 RoutingDataSource，打破循环依赖
        // 此处 DynamicDataSourceManager 和 RoutingDataSource 都已就绪，安全注入
        routingDataSource.setDataSourceManager(dataSourceManager);
        log.info("动态数据源 SqlSessionFactory 管理器已启用（路由数据源模式，复用主 SqlSessionFactory）");
        return new DynamicSqlSessionFactoryManager(dataSourceManager, primarySqlSessionFactory, routingDataSource);
    }

    /**
     * 动态 JDBC 模板
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicJdbcTemplate dynamicJdbcTemplate(
            DynamicDataSourceManager dataSourceManager,
            DbDialectRegistry dbDialectRegistry,
            org.springframework.beans.factory.ObjectProvider<DynamicSqlSessionFactoryManager> factoryManagerProvider) {
        DynamicSqlSessionFactoryManager factoryManager = factoryManagerProvider.getIfAvailable();
        log.info("动态 JDBC 模板已启用（参数化查询 + 原始SQL + 分页 + 注入检测 + 存储过程 + 方言适配 + MyBatis多数据源）");
        return new DynamicJdbcTemplate(dataSourceManager, dbDialectRegistry, factoryManager);
    }
}
