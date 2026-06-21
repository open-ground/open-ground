package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DbCheck 工具类
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class DbCheckUtils {

    /**
     * 根据配置创建 JDBC 连接
     *
     * @param properties DbCheck 配置
     * @return JDBC 连接（调用方负责关闭）
     */
    public Connection createConnection(DbCheckProperties properties) {
        String url = properties.getJdbcUrl();
        String username = properties.getJdbcUsername();
        String password = properties.getJdbcPassword();
        String driver = properties.getJdbcDriver();

        if (url == null || url.isEmpty()) {
            log.warn("JDBC URL 未配置，无法创建外部连接");
            return null;
        }

        try {
            if (driver != null && !driver.isEmpty()) {
                Class.forName(driver);
            }
            Connection conn = DriverManager.getConnection(url, username, password);
            conn.setAutoCommit(false);
            return conn;
        } catch (ClassNotFoundException e) {
            log.error("JDBC 驱动类未找到: {}", driver, e);
            return null;
        } catch (SQLException e) {
            log.error("JDBC 连接失败: {}", url, e);
            return null;
        }
    }

    /**
     * 关闭连接（静默处理异常）
     */
    public void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
