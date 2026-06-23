package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据库类型检测器
 * 复用 Flyway 的数据库类型检测逻辑
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class DatabaseTypeDetector {

    private static final String DB_TYPE_MYSQL = "mysql";
    private static final String DB_TYPE_ORACLE = "oracle";
    private static final String DB_TYPE_DM = "dm";
    private static final String DB_TYPE_POSTGRESQL = "postgresql";

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    /**
     * 检测数据库类型
     *
     * @return 数据库类型 (mysql/oracle/dm/postgresql)
     */
    public String detectDatabaseType() {
        if (datasourceUrl == null || datasourceUrl.isEmpty()) {
            log.info("spring.datasource.url is empty, defaulting to {}", DB_TYPE_MYSQL);
            return DB_TYPE_MYSQL;
        }
        
        String url = datasourceUrl.toLowerCase();
        if (url.contains(":mysql:")) {
            return DB_TYPE_MYSQL;
        }
        if (url.contains(":oracle:")) {
            return DB_TYPE_ORACLE;
        }
        if (url.contains(":dm:")) {
            return DB_TYPE_DM;
        }
        if (url.contains(":postgresql:") || url.contains(":gaussdb:")) {
            return DB_TYPE_POSTGRESQL;
        }
        
        log.info("Unknown database type in URL: {}, defaulting to {}", datasourceUrl, DB_TYPE_MYSQL);
        return DB_TYPE_MYSQL;
    }

    /**
     * 根据数据库类型获取对应的 SQL 文件扩展名
     *
     * @param dbType 数据库类型
     * @return SQL 文件扩展名
     */
    public String getFileExtension(String dbType) {
        switch (dbType) {
            case DB_TYPE_ORACLE:
                return ".sql";
            case DB_TYPE_DM:
                return ".sql";
            case DB_TYPE_POSTGRESQL:
                return ".sql";
            case DB_TYPE_MYSQL:
            default:
                return ".sql";
        }
    }
}
