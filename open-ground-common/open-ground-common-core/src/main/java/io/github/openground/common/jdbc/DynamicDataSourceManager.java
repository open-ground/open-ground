package io.github.openground.common.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多数据源管理器
 *
 * <p>基于 {@link DataSourceProviderRegistry} 统一管理数据源，使用 Druid 连接池。
 * 支持两种数据源来源：
 * <ul>
 *   <li>{@code ground.dblist} 配置式 — {@link ConfigDataSourceProvider}</li>
 *   <li>{@code sys_datasource} 表式 — SysDatasourceProvider</li>
 * </ul>
 *
 * <p>同名数据源优先级：sys_datasource 表(order=20) > dblist 配置(order=10)。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
public class DynamicDataSourceManager {

    private final DataSourceProviderRegistry registry;
    private final DruidProperties druidProperties;
    private final DataSource primaryDataSource;
    private final Map<String, DruidDataSource> dataSourceMap = new ConcurrentHashMap<>();

    public DynamicDataSourceManager(DataSourceProviderRegistry registry, DruidProperties druidProperties,
                                     DataSource primaryDataSource) {
        this.registry = registry;
        this.druidProperties = druidProperties;
        this.primaryDataSource = primaryDataSource;
    }

    /**
     * 获取 Druid 数据源（按需创建连接池）
     *
     * @param dsName 数据源名称
     * @return Druid 数据源
     * @throws IllegalArgumentException 如果数据源不存在
     */
    public DruidDataSource getDataSource(String dsName) {
        return dataSourceMap.computeIfAbsent(dsName, this::createDataSource);
    }

    /**
     * 获取默认数据源
     *
     * <p>优先返回 dblist/sys_datasource 中的第一个数据源，
     * 若无动态数据源则返回 Spring Boot 自动装配的主数据源。
     *
     * @return 数据源
     */
    public DataSource getDefaultDataSource() {
        String defaultDsName = registry.getDefaultDsName();
        if (defaultDsName != null) {
            return getDataSource(defaultDsName);
        }
        if (primaryDataSource != null) {
            return primaryDataSource;
        }
        throw new IllegalStateException("无可用数据源：未配置 ground.dblist/sys_datasource，且 Spring 主数据源不存在");
    }

    /**
     * 获取数据库类型（无参，使用默认数据源）
     *
     * @return 数据库类型字符串
     */
    public String getDbType() {
        String defaultDsName = registry.getDefaultDsName();
        if (defaultDsName != null) {
            return getDbType(defaultDsName);
        }
        // 从主数据源推断
        if (primaryDataSource != null) {
            return inferDbTypeFromDataSource(primaryDataSource);
        }
        return DbTypeDetector.MYSQL;
    }

    /**
     * 获取数据源描述符
     *
     * @param dsName 数据源名称
     * @return 描述符，不存在返回 null
     */
    public DataSourceDescriptor getDescriptor(String dsName) {
        return registry.getDescriptor(dsName);
    }

    /**
     * 获取数据库类型
     *
     * <p>优先使用显式配置的 dbType，否则从 URL/driverClassName 推断。
     *
     * @param dsName 数据源名称
     * @return 数据库类型字符串
     */
    public String getDbType(String dsName) {
        DataSourceDescriptor desc = registry.getDescriptor(dsName);
        if (desc == null) {
            throw new IllegalArgumentException("数据源 [" + dsName + "] 未配置");
        }
        return DbTypeDetector.detect(desc.getDbType(), desc.getUrl(), desc.getDriverClassName());
    }

    /**
     * 获取默认数据源名称
     *
     * @return 默认数据源名称，无则 null
     */
    public String getDefaultDsName() {
        return registry.getDefaultDsName();
    }

    /**
     * 获取默认数据源类型
     *
     * @return 数据库类型字符串，无数据源则默认 mysql
     */
    public String getDefaultDbType() {
        String dsName = getDefaultDsName();
        if (dsName != null) {
            return getDbType(dsName);
        }
        return DbTypeDetector.MYSQL;
    }

    /**
     * 获取所有已注册的数据源名称
     *
     * @return 数据源名称集合
     */
    public Set<String> getDataSourceNames() {
        return registry.getDataSourceNames();
    }

    /**
     * 获取数据源列表（脱敏）
     *
     * @return 数据源信息列表
     */
    public List<Map<String, String>> getDataSourceList() {
        return registry.getAllDescriptors().stream()
                .map(desc -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("dsName", desc.getDsName());
                    map.put("dbName", desc.getDbName());
                    map.put("app", desc.getApp());
                    map.put("dbType", getDbType(desc.getDsName()));
                    map.put("source", desc.getSource());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 刷新数据源缓存
     *
     * <p>重新从 Provider 收集数据源信息，关闭旧连接池，按需创建新连接池。
     */
    public synchronized void refresh() {
        registry.refresh();
        // 关闭不再存在的连接池
        Set<String> currentNames = registry.getDataSourceNames();
        for (String name : new HashSet<>(dataSourceMap.keySet())) {
            if (!currentNames.contains(name)) {
                DruidDataSource ds = dataSourceMap.remove(name);
                if (ds != null) {
                    ds.close();
                    log.info("数据源 [{}] 连接池已关闭（不再存在）", name);
                }
            }
        }
    }

    /**
     * 创建 Druid 连接池
     * <p>
     * 支持从 DataSourceDescriptor.rawEntity 中提取 per-datasource 的连接池参数，
     * 未配置则用全局 spring.datasource.druid.* 默认值。
     */
    private DruidDataSource createDataSource(String dsName) {
        DataSourceDescriptor desc = registry.getDescriptor(dsName);
        if (desc == null) {
            throw new IllegalArgumentException("数据源 [" + dsName + "] 未在配置或 sys_datasource 表中定义");
        }
        String driverClassName = desc.getDriverClassName();
        if (!StringUtils.hasText(driverClassName)) {
            driverClassName = inferDriverClassName(desc.getDbType(), desc.getUrl());
        }
        // 提取 per-datasource 连接池参数（如果 rawEntity 中有）
        Integer overrideInitialSize = null;
        Integer overrideMaxActive = null;
        Integer overrideMinIdle = null;
        Long overrideMaxWait = null;
        Object raw = desc.getRawEntity();
        if (raw instanceof DynamicDataSourceProperties.DataSourceEntry entry) {
            overrideInitialSize = entry.getInitialSize() != 5 ? entry.getInitialSize() : null;
            overrideMaxActive = entry.getMaxActive() != 20 ? entry.getMaxActive() : null;
            overrideMinIdle = entry.getMinIdle() != 5 ? entry.getMinIdle() : null;
            overrideMaxWait = entry.getMaxWait() != 30000 ? entry.getMaxWait() : null;
        }
        DruidDataSource ds = druidProperties.dataSource(desc.getUrl(), desc.getUsername(),
                desc.getPassword(), driverClassName,
                overrideInitialSize, overrideMaxActive, overrideMinIdle, overrideMaxWait);
        log.info("动态数据源 [{}] Druid 连接池已创建: url={}, source={}", dsName, desc.getUrl(), desc.getSource());
        return ds;
    }

    /**
     * 根据 dbType 或 URL 推断驱动类名
     */
    private String inferDriverClassName(String dbType, String url) {
        String type = DbTypeDetector.detect(dbType, url, null);
        switch (type) {
            case DbTypeDetector.ORACLE:
                return "oracle.jdbc.OracleDriver";
            case DbTypeDetector.POSTGRESQL:
                return "org.postgresql.Driver";
            case DbTypeDetector.GAUSSDB:
                return "org.opengauss.Driver";
            case DbTypeDetector.DM:
                return "dm.jdbc.driver.DmDriver";
            case DbTypeDetector.SQLSERVER:
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case DbTypeDetector.DB2:
                return "com.ibm.db2.jcc.DB2Driver";
            case DbTypeDetector.MYSQL:
            default:
                return "com.mysql.cj.jdbc.Driver";
        }
    }

    /**
     * 从 DataSource 推断数据库类型（用于主数据源）
     */
    private String inferDbTypeFromDataSource(DataSource ds) {
        if (ds instanceof DruidDataSource dds) {
            String url = dds.getUrl();
            if (url != null) {
                return DbTypeDetector.detectByUrl(url);
            }
            String driverClassName = dds.getDriverClassName();
            if (driverClassName != null) {
                return DbTypeDetector.detectByDriverClassName(driverClassName);
            }
        }
        // 尝试获取连接信息
        try (Connection conn = ds.getConnection()) {
            String url = conn.getMetaData().getURL();
            return DbTypeDetector.detectByUrl(url);
        } catch (Exception e) {
            log.debug("无法从主数据源推断数据库类型，默认 mysql", e);
            return DbTypeDetector.MYSQL;
        }
    }

    /**
     * 关闭所有连接池
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭所有动态数据源连接池...");
        dataSourceMap.forEach((name, ds) -> {
            ds.close();
            log.info("数据源 [{}] 连接池已关闭", name);
        });
        dataSourceMap.clear();
    }
}
