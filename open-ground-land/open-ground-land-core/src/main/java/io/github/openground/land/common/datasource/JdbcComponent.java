package io.github.openground.land.common.datasource;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.pool.DruidDataSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 多数据源组件
 * <p>支持动态管理多个 Druid 数据源连接池，提供 SQL 执行、存储过程调用等功能。
 * 数据源配置通过 {@code ground.dblist} 配置项定义。</p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "ground")
public class JdbcComponent {

    @Autowired
    private DruidProperties druidProperties;

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Setter
    private List<Map<String, String>> dblist;

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        INSERT, UPDATE, SELECT, DELETE, COUNT
    }

    /** 所有数据源的连接池缓存 */
    private final Map<String, DruidDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    /**
     * 获取指定数据源名称的数据库类型
     *
     * @param dsName 数据源名称
     * @return 数据库类型（mysql/oracle/postgresql 等），未找到返回空字符串
     */
    public String getDbType(String dsName) {
        if (!ObjectUtil.isNotEmpty(dblist)) {
            throw new IllegalArgumentException("数据源：" + dsName + "未配置，请检查ground.dblist配置信息");
        }
        for (Map<String, String> mapone : dblist) {
            if (mapone.get("dsName").equals(dsName)) {
                if (mapone.containsKey("dbType")) {
                    return mapone.get("dbType");
                }
                String driverClassName = mapone.getOrDefault("driver-class-name", dataSourceProperties.getDriverClassName());
                if (driverClassName.contains("mysql")) {
                    return "mysql";
                } else if (driverClassName.contains("oracle")) {
                    return "oracle";
                } else if (driverClassName.contains("postgresql")) {
                    return "postgresql";
                } else if (driverClassName.contains("sqlserver")) {
                    return "sqlserver";
                } else if (driverClassName.contains("db2")) {
                    return "db2";
                } else if (driverClassName.contains("gaussdb")) {
                    return "gaussdb";
                }
            }
        }
        return "";
    }

    /**
     * 根据数据源名称获取连接
     *
     * @param dsName 数据源名称
     * @return 数据库连接
     */
    public Connection getPooledConnection(String dsName) throws SQLException {
        DruidDataSource pool = getJdbcConnectionPool(dsName);
        return pool.getConnection();
    }

    /**
     * 根据数据源名称获取或创建连接池
     *
     * @param dsName 数据源名称
     * @return Druid 数据源连接池
     */
    public DruidDataSource getJdbcConnectionPool(String dsName) {
        if (dataSourceMap.containsKey(dsName)) {
            log.info("getPooledConnection dsName:{} from cached map", dsName);
            return dataSourceMap.get(dsName);
        }

        String driveName = dataSourceProperties.getDriverClassName();
        String url = dataSourceProperties.getUrl();
        String userName = "";
        String password = "";

        for (Map<String, String> mapone : dblist) {
            if (mapone.get("dsName").equals(dsName)) {
                userName = mapone.get("username");
                password = mapone.get("password");
                String dbName = mapone.get("dbName");
                String dbUrl = mapone.get("url");
                if (StrUtil.isBlank(dbUrl)) {
                    if (driveName.contains("mysql")) {
                        dbUrl = url;
                        dbUrl = dbUrl.substring(0, dbUrl.indexOf("?"));
                        int index1 = dbUrl.lastIndexOf("/");
                        int index2 = url.indexOf("?");
                        url = url.substring(0, index1 + 1) + dbName + url.substring(index2);
                    } else {
                        throw new IllegalArgumentException("数据源：" + dsName + "未配置url");
                    }
                } else {
                    url = dbUrl;
                }
                String driverClassName = mapone.getOrDefault("driverClassName", mapone.get("driver-class-name"));
                if (StrUtil.isNotEmpty(driverClassName)) {
                    driveName = driverClassName;
                }
            }
        }

        synchronized (lock) {
            if (!dataSourceMap.containsKey(dsName)) {
                DruidDataSource pool = druidProperties.dataSource(url, userName, password, driveName);
                dataSourceMap.put(dsName, pool);
                log.info("创建连接池成功：{}", url);
            }
            return dataSourceMap.get(dsName);
        }
    }

    /**
     * 执行 SQL 语句
     *
     * @param dsName 数据源名称
     * @param sql    SQL 语句
     * @param type   操作类型
     * @return 查询结果列表（SELECT/COUNT）或影响行数（INSERT/UPDATE/DELETE）
     */
    public List<Map<String, Object>> execSql(String dsName, String sql, OperationType type) {
        try (
                Connection pooledConnection = getPooledConnection(dsName);
                PreparedStatement statement = pooledConnection.prepareStatement(sql);
        ) {
            ResultSet rs = null;
            Integer rows = null;
            switch (type) {
                case INSERT:
                case UPDATE:
                case DELETE:
                    rows = statement.executeUpdate();
                    break;
                case SELECT:
                case COUNT:
                    rs = statement.executeQuery();
                    break;
            }

            List<Map<String, Object>> result = new ArrayList<>();
            if (Objects.nonNull(rs)) {
                List<Map<String, Object>> columns = new ArrayList<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    Map<String, Object> temp = new HashMap<>();
                    temp.put("name", rs.getMetaData().getColumnLabel(i));
                    temp.put("setType", rs.getMetaData().getColumnTypeName(i));
                    temp.put("setLength", rs.getMetaData().getColumnDisplaySize(i));
                    columns.add(temp);
                }
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    for (Map<String, Object> col : columns) {
                        String name = col.get("name").toString();
                        Object value = rs.getObject(name);
                        // 解决 oracle 导出 NCLOB 错误问题
                        if (value instanceof NClob) {
                            NClob nclob = rs.getNClob(name);
                            value = nclob.getSubString(1, (int) nclob.length());
                        } else if (value instanceof Clob) {
                            Clob clob = rs.getClob(name);
                            value = clob.getSubString(1, (int) clob.length());
                        }
                        // 修复 pg、达梦数据库获取行数错误问题
                        if ("count(0)".equals(name) || "COUNT(0)".equals(name)) {
                            name = "count";
                        }
                        item.put(name, value);
                    }
                    result.add(item);
                }
            }
            if (Objects.nonNull(rows)) {
                Map<String, Object> item = new HashMap<>();
                item.put("rows", rows);
                result.add(item);
            }
            return result;
        } catch (SQLException e) {
            log.error("SQL执行异常", e);
            throw new IllegalArgumentException("SQL执行异常", e);
        } catch (Exception e) {
            log.error("执行JDBC查询时出现异常", e);
            throw new IllegalArgumentException("执行JDBC查询时出现异常", e);
        }
    }

    /**
     * 调用存储过程
     *
     * @param dsName 数据源名称
     * @param sql    存储过程调用 SQL
     * @return 输出参数结果（retCode, retMsg）
     */
    public Map<String, Object> execProcdureSql(String dsName, String sql) throws Exception {
        Map<String, Object> result = new HashMap<>();
        try (
                Connection pooledConnection = getPooledConnection(dsName);
                CallableStatement cstmt = pooledConnection.prepareCall(sql);
        ) {
            cstmt.registerOutParameter(1, Types.VARCHAR);
            cstmt.registerOutParameter(2, Types.VARCHAR);

            log.info("执行存储过程SQL: {}", sql);
            cstmt.execute();

            String retCode = cstmt.getString(1);
            String retMsg = cstmt.getString(2);
            result.put("retCode", retCode);
            result.put("retMsg", retMsg);
            return result;
        }
    }

    /**
     * 根据数据库类型生成存储过程调用 SQL
     *
     * @param dbType   数据库类型
     * @param procName 存储过程名称
     * @param eodDate  日期参数
     * @return 存储过程调用 SQL
     */
    @NonNull
    public static String getProcSql(String dbType, String procName, String eodDate) {
        String sql;
        if ("ORACLE".equalsIgnoreCase(dbType)) {
            sql = "call " + procName + " ('" + eodDate + "', ?, ?)";
        } else if ("MySQL".equalsIgnoreCase(dbType)) {
            sql = "call " + procName + " ('" + eodDate + "', ?, ?)";
        } else if ("PostgreSQL".equalsIgnoreCase(dbType)) {
            sql = "{call " + procName + " ('" + eodDate + "', ?, ?)}";
        } else {
            sql = "call " + procName + " ('" + eodDate + "', ?, ?)";
        }
        log.info("dbType:{},生成的存储过程SQL: {}", dbType, sql);
        return sql;
    }

    /**
     * 构造存储过程错误信息
     *
     * @param errorMsg 错误信息
     * @param procName 存储过程名称
     * @return 友好的错误提示
     */
    @NonNull
    public static String getMessage(String errorMsg, String procName) {
        String message;
        if (errorMsg.contains("Parameter index of")) {
            message = "存储过程参数错误：参数格式：入参8位字符串日期，出参字符串类型RET_CODE, RET_MSG。请检查参数配置";
        } else if (errorMsg.contains("does not exist")) {
            message = "存储过程[" + procName + "]不存在";
        } else if (errorMsg.contains("Parameter number 1 is not an OUT parameter")
                || errorMsg.contains("Parameter number 2 is not an OUT parameter")
                || errorMsg.contains("Parameter number 3 is not an OUT parameter")) {
            message = "存储过程[" + procName + "]可能不存在,或参数格式错误：入参8位字符串日期，出参字符串类型RET_CODE, RET_MSG.请检查参数配置:";
        } else {
            message = "数据库操作失败";
        }
        return message;
    }
}
