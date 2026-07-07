package io.github.openground.common.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源 SqlSessionFactory 管理器
 *
 * <p>为每个动态数据源创建独立的 {@link SqlSessionFactory}，复用 Spring 主
 * SqlSessionFactory 的配置（Mapper XML、类型别名、插件等），仅替换 DataSource。
 *
 * <p>每个 dsName 对应一个 SqlSessionFactory，缓存复用，不重复创建。
 * 与 Spring 主 SqlSessionFactory 完全隔离，互不影响。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
public class DynamicSqlSessionFactoryManager {

    private final DynamicDataSourceManager dataSourceManager;
    private final SqlSessionFactory primarySqlSessionFactory;
    private final ConcurrentHashMap<String, SqlSessionFactory> factoryCache = new ConcurrentHashMap<>();

    /**
     * @param dataSourceManager       动态数据源管理器
     * @param primarySqlSessionFactory Spring 容器中的主 SqlSessionFactory（用于复用配置）
     */
    public DynamicSqlSessionFactoryManager(DynamicDataSourceManager dataSourceManager,
                                           SqlSessionFactory primarySqlSessionFactory) {
        this.dataSourceManager = dataSourceManager;
        this.primarySqlSessionFactory = primarySqlSessionFactory;
    }

    /**
     * 获取或创建指定数据源的 SqlSessionFactory
     *
     * <p>复用主 SqlSessionFactory 的 Configuration（Mapper XML、类型别名、插件等），
     * 仅替换 Environment 中的 DataSource。
     *
     * @param dsName 数据源名称
     * @return SqlSessionFactory
     */
    public SqlSessionFactory getSqlSessionFactory(String dsName) {
        return factoryCache.computeIfAbsent(dsName, k -> {
            DataSource ds = dataSourceManager.getDataSource(k);
            org.apache.ibatis.session.Configuration config = primarySqlSessionFactory.getConfiguration();
            // 替换 Environment 中的 DataSource，保留原有配置
            Environment env = new Environment("dynamic-" + k, new JdbcTransactionFactory(), ds);
            config.setEnvironment(env);
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
            log.info("动态数据源 [{}] SqlSessionFactory 已创建", k);
            return factory;
        });
    }

    /**
     * 清除指定数据源的 SqlSessionFactory 缓存
     *
     * @param dsName 数据源名称
     */
    public void evict(String dsName) {
        factoryCache.remove(dsName);
    }

    /**
     * 清除所有缓存
     */
    public void evictAll() {
        factoryCache.clear();
    }
}
