package io.github.openground.common.jdbc.dialect;

import io.github.openground.common.jdbc.DbTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库方言注册中心
 *
 * <p>自动收集所有 {@link DbDialect} 实现，按 {@link DbDialect#getDbType()} 注册。
 * 调用方通过 {@link #getDialect(String)} 获取对应数据库的方言实现。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Component
public class DbDialectRegistry {

    private final Map<String, DbDialect> dialectMap = new ConcurrentHashMap<>();

    @Autowired
    public DbDialectRegistry(List<DbDialect> dialects) {
        for (DbDialect dialect : dialects) {
            dialectMap.put(dialect.getDbType(), dialect);
            log.info("注册数据库方言: {} -> {}", dialect.getDbType(), dialect.getClass().getSimpleName());
        }
    }

    /**
     * 根据数据库类型获取方言
     *
     * @param dbType 数据库类型
     * @return 方言实现，未找到则返回 MySQL 方言
     */
    public DbDialect getDialect(String dbType) {
        DbDialect dialect = dialectMap.get(dbType);
        if (dialect == null) {
            dialect = dialectMap.get(DbTypeDetector.MYSQL);
        }
        return dialect;
    }

    /**
     * 获取所有已注册的数据库类型
     *
     * @return 数据库类型集合
     */
    public java.util.Set<String> getRegisteredTypes() {
        return dialectMap.keySet();
    }
}
