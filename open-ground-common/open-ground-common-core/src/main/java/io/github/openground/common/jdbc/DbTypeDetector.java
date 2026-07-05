package io.github.openground.common.jdbc;

/**
 * 数据库类型检测器
 *
 * <p>统一的数据库类型推断工具，替代各项目中重复的类型判断逻辑。
 * 支持 MySQL、Oracle、PostgreSQL、达梦(DM)、GaussDB、SQL Server、DB2。
 *
 * @author open-ground
 * @since 1.0.2
 */
public final class DbTypeDetector {

    public static final String MYSQL = "mysql";
    public static final String ORACLE = "oracle";
    public static final String POSTGRESQL = "postgresql";
    public static final String DM = "dm";
    public static final String GAUSSDB = "gaussdb";
    public static final String SQLSERVER = "sqlserver";
    public static final String DB2 = "db2";

    private DbTypeDetector() {
    }

    /**
     * 根据 driverClassName 推断数据库类型
     *
     * @param driverClassName JDBC 驱动类名
     * @return 数据库类型字符串，默认 mysql
     */
    public static String detectByDriverClassName(String driverClassName) {
        if (driverClassName == null || driverClassName.isEmpty()) {
            return MYSQL;
        }
        String d = driverClassName.toLowerCase();
        if (d.contains("mysql")) return MYSQL;
        if (d.contains("oracle")) return ORACLE;
        if (d.contains("postgresql")) return POSTGRESQL;
        if (d.contains("sqlserver")) return SQLSERVER;
        if (d.contains("db2")) return DB2;
        if (d.contains("gaussdb")) return GAUSSDB;
        if (d.contains("dm.")) return DM;
        return MYSQL;
    }

    /**
     * 根据 JDBC URL 推断数据库类型
     *
     * @param url JDBC 连接 URL
     * @return 数据库类型字符串，默认 mysql
     */
    public static String detectByUrl(String url) {
        if (url == null || url.isEmpty()) {
            return MYSQL;
        }
        String u = url.toLowerCase();
        if (u.contains(":mysql:")) return MYSQL;
        if (u.contains(":oracle:")) return ORACLE;
        if (u.contains(":postgresql:")) return POSTGRESQL;
        if (u.contains(":gaussdb:")) return GAUSSDB;
        if (u.contains(":dm:")) return DM;
        if (u.contains(":sqlserver:")) return SQLSERVER;
        if (u.contains(":db2:")) return DB2;
        return MYSQL;
    }

    /**
     * 综合推断数据库类型
     *
     * <p>优先使用显式配置的 dbType，其次从 URL 推断，最后从 driverClassName 推断。
     *
     * @param dbType          显式配置的数据库类型（可为 null）
     * @param url             JDBC URL（可为 null）
     * @param driverClassName 驱动类名（可为 null）
     * @return 数据库类型字符串
     */
    public static String detect(String dbType, String url, String driverClassName) {
        if (dbType != null && !dbType.isEmpty()) {
            return dbType;
        }
        if (url != null && !url.isEmpty()) {
            return detectByUrl(url);
        }
        return detectByDriverClassName(driverClassName);
    }
}
