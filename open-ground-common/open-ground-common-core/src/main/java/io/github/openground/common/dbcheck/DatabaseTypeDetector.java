package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * 数据库类型检测器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class DatabaseTypeDetector {

    /**
     * 检测数据库类型
     *
     * @param conn JDBC 连接
     * @return 数据库类型（mysql/oracle/dm/postgresql/gaussdb）
     */
    public String detect(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName().toLowerCase();
            String driverVersion = meta.getDriverVersion().toLowerCase();

            if (productName.contains("mysql") || productName.contains("mariadb")) {
                return "mysql";
            } else if (productName.contains("oracle")) {
                return "oracle";
            } else if (productName.contains("dameng") || productName.contains("dm")) {
                return "dm";
            } else if (productName.contains("postgresql") || productName.contains("postgres")) {
                // 区分 GaussDB 和 PostgreSQL
                if (driverVersion.contains("gauss") || driverVersion.contains("opengauss")) {
                    return "gaussdb";
                }
                return "postgresql";
            } else if (productName.contains("gauss") || productName.contains("opengauss")) {
                return "gaussdb";
            } else if (productName.contains("h2")) {
                return "h2";
            }

            log.warn("未能识别的数据库类型: productName={}", productName);
            return "unknown";
        } catch (SQLException e) {
            log.error("检测数据库类型失败", e);
            return "unknown";
        }
    }

    /**
     * 根据数据库类型获取 JDBC 驱动类名
     */
    public String getDriverClassName(String dbType) {
        switch (dbType) {
            case "mysql": return "com.mysql.cj.jdbc.Driver";
            case "oracle": return "oracle.jdbc.OracleDriver";
            case "dm": return "dm.jdbc.driver.DmDriver";
            case "postgresql":
            case "gaussdb": return "org.postgresql.Driver";
            default: return "com.mysql.cj.jdbc.Driver";
        }
    }

    /**
     * 根据数据库类型获取 JDBC URL 前缀
     */
    public String getJdbcUrlPrefix(String dbType) {
        switch (dbType) {
            case "mysql": return "jdbc:mysql://";
            case "oracle": return "jdbc:oracle:thin:@";
            case "dm": return "jdbc:dm://";
            case "postgresql":
            case "gaussdb": return "jdbc:postgresql://";
            default: return "jdbc:mysql://";
        }
    }
}
