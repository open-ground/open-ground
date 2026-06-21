package io.github.openground.common.dbcheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * DbCheck 配置属性
 * 支持多数据库类型检查和数据同步配置
 *
 * @author open-ground
 * @since 2026-06-18
 */
@ConfigurationProperties("ground.db-check")
public class DbCheckProperties {

    /**
     * 是否启用 DbCheck 功能
     */
    private boolean enabled = false;

    /**
     * 检查模式
     * check-only: 仅检查，不执行覆盖
     * check-and-cover: 检查并覆盖不一致的表结构
     */
    private String mode = "check-only";

    /**
     * SQL 脚本目录（相对于 classpath）
     */
    private String scriptDir = "db/auth";

    /**
     * 脚本文件编码
     */
    private String encoding = "UTF-8";

    /**
     * 数据源 JNDI 名称（c3p0 数据源专用）
     */
    private String jndiName = "java:comp/env/jdbc/builder";

    /**
     * JDBC URL（外部数据源直连）
     */
    private String jdbcUrl;

    /**
     * JDBC 用户名
     */
    private String jdbcUsername;

    /**
     * JDBC 密码
     */
    private String jdbcPassword;

    /**
     * JDBC 驱动类名
     */
    private String jdbcDriver;

    /**
     * 忽略检查的表名列表
     */
    private List<String> ignoreTables = new ArrayList<>();

    /**
     * 数据同步配置
     */
    private DataSyncConfig dataSync = new DataSyncConfig();

    /**
     * 数据同步配置
     */
    public static class DataSyncConfig {
        /**
         * 是否启用数据同步
         */
        private boolean enabled = false;

        /**
         * 数据同步脚本目录
         */
        private String dataDir = "db/auth/data";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDataDir() { return dataDir; }
        public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    }

    // ===== Getters & Setters =====

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getScriptDir() { return scriptDir; }
    public void setScriptDir(String scriptDir) { this.scriptDir = scriptDir; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public String getJndiName() { return jndiName; }
    public void setJndiName(String jndiName) { this.jndiName = jndiName; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getJdbcUsername() { return jdbcUsername; }
    public void setJdbcUsername(String jdbcUsername) { this.jdbcUsername = jdbcUsername; }

    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }

    public String getJdbcDriver() { return jdbcDriver; }
    public void setJdbcDriver(String jdbcDriver) { this.jdbcDriver = jdbcDriver; }

    public List<String> getIgnoreTables() { return ignoreTables; }
    public void setIgnoreTables(List<String> ignoreTables) { this.ignoreTables = ignoreTables; }

    public DataSyncConfig getDataSync() { return dataSync; }
    public void setDataSync(DataSyncConfig dataSync) { this.dataSync = dataSync; }
}
