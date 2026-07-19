package io.github.openground.common.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 动态数据源 SqlSessionFactory 管理器
 *
 * <p>open-ground 的 MyBatis 动态多数据源方案基于<b>路由数据源</b>（{@link RoutingDataSource}），
 * 而非"每个数据源一个独立 SqlSessionFactory"。本管理器持有 Spring 主 SqlSessionFactory 和
 * {@link RoutingDataSource}，通过切换路由数据源的 ThreadLocal 上下文实现数据源路由，
 * 完全复用主 SqlSessionFactory 的 Configuration（MappedStatement、类型别名、拦截器、
 * MyBatis-Plus 的 TableInfo/GlobalConfig 等），零重建、零污染。
 *
 * <h3>设计理念</h3>
 * <p>MyBatis 执行 SQL 时，连接由 {@code Configuration.getEnvironment().getDataSource()} 决定。
 * 早期实现直接调用 {@code primarySqlSessionFactory.getConfiguration().setEnvironment(env)}
 * 替换数据源，但 Configuration 是全局共享单例，这会污染主 SqlSessionFactory，导致所有走主工厂的
 * 业务 Mapper 误连动态数据源。此 bug 已在 1.0.7 修复，改为路由数据源方案。
 *
 * <p>当前 {@link #getSqlSessionFactory(String)} 统一返回主 SqlSessionFactory，数据源切换由
 * {@link RoutingDataSource} 在连接获取时按 ThreadLocal 上下文路由完成。
 * {@link DynamicJdbcTemplate} 的 MyBatis 方法在调用前后通过
 * {@code routingDataSource.push(dsName) / pop()} 管理上下文。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
public class DynamicSqlSessionFactoryManager {

    private final DynamicDataSourceManager dataSourceManager;
    private final SqlSessionFactory primarySqlSessionFactory;
    private final RoutingDataSource routingDataSource;

    /**
     * @param dataSourceManager        动态数据源管理器（用于解析 dsName 到真实数据源）
     * @param primarySqlSessionFactory Spring 容器中的主 SqlSessionFactory（复用其全部配置）
     * @param routingDataSource        路由数据源（主 SqlSessionFactory 的 Environment 持有它）
     */
    public DynamicSqlSessionFactoryManager(DynamicDataSourceManager dataSourceManager,
                                           SqlSessionFactory primarySqlSessionFactory,
                                           RoutingDataSource routingDataSource) {
        this.dataSourceManager = dataSourceManager;
        this.primarySqlSessionFactory = primarySqlSessionFactory;
        this.routingDataSource = routingDataSource;
    }

    /**
     * 获取主 SqlSessionFactory
     *
     * <p>统一返回主 SqlSessionFactory，数据源路由由 {@link RoutingDataSource} 完成。
     * dsName 参数保留以兼容现有 API，实际数据源切换在 {@link DynamicJdbcTemplate} 中
     * 通过 {@code routingDataSource.push(dsName)} 实现。
     *
     * @param dsName 数据源名称（保留兼容，实际路由由 RoutingDataSource 完成）
     * @return 主 SqlSessionFactory
     */
    public SqlSessionFactory getSqlSessionFactory(String dsName) {
        return primarySqlSessionFactory;
    }

    /**
     * 获取路由数据源
     *
     * <p>供 {@link DynamicJdbcTemplate} 管理数据源上下文（push/pop）使用。
     *
     * @return 路由数据源实例
     */
    public RoutingDataSource getRoutingDataSource() {
        return routingDataSource;
    }

    /**
     * 获取动态数据源管理器
     *
     * @return 动态数据源管理器
     */
    public DynamicDataSourceManager getDataSourceManager() {
        return dataSourceManager;
    }
}
