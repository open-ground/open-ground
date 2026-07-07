package io.github.openground.common.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import io.github.openground.common.jdbc.dialect.DbDialect;
import io.github.openground.common.jdbc.dialect.DbDialectRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * 动态 JDBC 模板 — 安全的参数化查询组件
 *
 * <p>基于 {@link NamedParameterJdbcTemplate} 实现参数化查询，配合 {@link DynamicDataSourceManager}
 * 按数据源名称路由。同时提供原始 SQL 执行（{@link #execSql}）、分页 SQL 生成、SQL 注入检测、
 * 存储过程调用等能力，合并自 ground-auth/imap-pub/land-core 的 JdbcComponent。
 *
 * <pre>
 * // 参数化查询
 * Map&lt;String, Object&gt; params = new HashMap&lt;&gt;();
 * params.put("name", "张三");
 * List&lt;Map&lt;String, Object&gt;&gt; result = tpl.queryForList("master",
 *     "SELECT * FROM user WHERE name = :name", params);
 *
 * // 原始 SQL 执行（兼容老代码）
 * List&lt;Map&lt;String, Object&gt;&gt; result = tpl.execSql("master",
 *     "SELECT * FROM user WHERE id = 1", OperationType.SELECT);
 *
 * // DDL 执行
 * tpl.executeDdl("master", "CREATE TABLE test (id INT)");
 * </pre>
 *
 * @author open-ground
 */
@Slf4j
public class DynamicJdbcTemplate {

    private final DynamicDataSourceManager dataSourceManager;
    private final DbDialectRegistry dbDialectRegistry;
    private final DynamicSqlSessionFactoryManager sqlSessionFactoryManager;

    /**
     * 操作类型枚举（兼容 PubJdbcComponent.OperationType）
     */
    public enum OperationType {
        INSERT, UPDATE, SELECT, DELETE, COUNT
    }

    public DynamicJdbcTemplate(DynamicDataSourceManager dataSourceManager, DbDialectRegistry dbDialectRegistry) {
        this.dataSourceManager = dataSourceManager;
        this.dbDialectRegistry = dbDialectRegistry;
        this.sqlSessionFactoryManager = null;
    }

    /**
     * 带动态 SqlSessionFactory 管理器的构造函数
     */
    public DynamicJdbcTemplate(DynamicDataSourceManager dataSourceManager, DbDialectRegistry dbDialectRegistry,
                                DynamicSqlSessionFactoryManager sqlSessionFactoryManager) {
        this.dataSourceManager = dataSourceManager;
        this.dbDialectRegistry = dbDialectRegistry;
        this.sqlSessionFactoryManager = sqlSessionFactoryManager;
    }

    /**
     * 获取数据源对应的 NamedParameterJdbcTemplate
     */
    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(String dsName) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        return new NamedParameterJdbcTemplate(Objects.requireNonNull(ds));
    }

    /**
     * 获取默认数据源的 NamedParameterJdbcTemplate（无需 dsName）
     */
    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        DataSource ds = dataSourceManager.getDefaultDataSource();
        return new NamedParameterJdbcTemplate(Objects.requireNonNull(ds));
    }

    /**
     * 获取数据源对应的普通 JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String dsName) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        return new JdbcTemplate(Objects.requireNonNull(ds));
    }

    /**
     * 获取默认数据源的普通 JdbcTemplate（无需 dsName）
     */
    public JdbcTemplate getJdbcTemplate() {
        DataSource ds = dataSourceManager.getDefaultDataSource();
        return new JdbcTemplate(Objects.requireNonNull(ds));
    }

    // ========== 参数化查询方法（无参版，使用默认数据源） ==========

    /**
     * 查询列表（使用默认数据源）
     */
    public List<Map<String, Object>> queryForList(String sql, Map<String, Object> params) {
        return getNamedParameterJdbcTemplate().queryForList(sql, params);
    }

    /**
     * 查询列表（使用默认数据源，无参数）
     */
    public List<Map<String, Object>> queryForList(String sql) {
        return queryForList(sql, Collections.emptyMap());
    }

    /**
     * 查询单条记录（使用默认数据源）
     */
    public Map<String, Object> queryForOne(String sql, Map<String, Object> params) {
        List<Map<String, Object>> list = queryForList(sql, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询单个值（使用默认数据源）
     */
    public <T> T queryForObject(String sql, Map<String, Object> params, Class<T> clazz) {
        return getNamedParameterJdbcTemplate().queryForObject(sql, params, clazz);
    }

    /**
     * 执行 UPDATE/INSERT/DELETE（使用默认数据源）
     */
    public int update(String sql, Map<String, Object> params) {
        return getNamedParameterJdbcTemplate().update(sql, params);
    }

    /**
     * 执行 DDL（使用默认数据源）
     */
    public void executeDdl(String ddl) {
        DataSource ds = dataSourceManager.getDefaultDataSource();
        Connection connection = DataSourceUtils.getConnection(ds);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(ddl);
            log.info("DDL 执行成功: {}", ddl);
        } catch (SQLException e) {
            throw new RuntimeException("执行 DDL 失败: " + ddl, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    /**
     * 执行原始 SQL（使用默认数据源）
     */
    public List<Map<String, Object>> execSql(String sql, OperationType type) {
        String defaultDsName = dataSourceManager.getDefaultDsName();
        if (defaultDsName != null) {
            return execSql(defaultDsName, sql, type);
        }
        // 无动态数据源，使用主数据源直接执行
        return execSqlWithPrimaryDataSource(sql, type);
    }

    /**
     * 生成分页 SQL（使用默认数据源）
     */
    public String handlePageSql(String querySelect, int pageIndex, int pageSize) {
        String dbType = dataSourceManager.getDbType();
        return SqlUtils.buildPageSql(dbType, querySelect, pageIndex, pageSize);
    }

    /**
     * 获取数据库方言（使用默认数据源）
     */
    public DbDialect getDialect() {
        String dbType = dataSourceManager.getDbType();
        return dbDialectRegistry.getDialect(dbType);
    }

    /**
     * 使用主数据源执行原始 SQL
     */
    private List<Map<String, Object>> execSqlWithPrimaryDataSource(String sql, OperationType type) {
        DataSource ds = dataSourceManager.getDefaultDataSource();
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            ResultSet rs = null;
            Integer rows = null;
            switch (type) {
                case INSERT: case UPDATE: case DELETE:
                    rows = statement.executeUpdate();
                    break;
                case SELECT: case COUNT:
                    rs = statement.executeQuery();
                    break;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            if (Objects.nonNull(rs)) {
                List<String> columnNames = new ArrayList<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    columnNames.add(rs.getMetaData().getColumnLabel(i));
                }
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    for (String name : columnNames) {
                        Object value = rs.getObject(name);
                        if (value instanceof NClob nclob) {
                            value = nclob.getSubString(1, (int) nclob.length());
                        } else if (value instanceof Clob clob) {
                            value = clob.getSubString(1, (int) clob.length());
                        }
                        if ("count(0)".equalsIgnoreCase(name)) {
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
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            log.error("执行JDBC查询时出现异常", e);
            throw new IllegalArgumentException(e);
        }
    }

    // ========== 参数化查询方法（指定数据源） ==========

    /**
     * 查询列表，返回 List&lt;Map&lt;String, Object&gt;&gt;
     */
    public List<Map<String, Object>> queryForList(String dsName, String sql, Map<String, Object> params) {
        NamedParameterJdbcTemplate tpl = getNamedParameterJdbcTemplate(dsName);
        return tpl.queryForList(sql, params);
    }

    /**
     * 查询列表（无参数）
     */
    public List<Map<String, Object>> queryForList(String dsName, String sql) {
        return queryForList(dsName, sql, Collections.emptyMap());
    }

    /**
     * 查询单条记录
     */
    public Map<String, Object> queryForOne(String dsName, String sql, Map<String, Object> params) {
        List<Map<String, Object>> list = queryForList(dsName, sql, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询单个值
     */
    public <T> T queryForObject(String dsName, String sql, Map<String, Object> params, Class<T> clazz) {
        NamedParameterJdbcTemplate tpl = getNamedParameterJdbcTemplate(dsName);
        return tpl.queryForObject(sql, params, clazz);
    }

    // ========== 参数化更新方法 ==========

    /**
     * 执行 INSERT/UPDATE/DELETE
     */
    public int update(String dsName, String sql, Map<String, Object> params) {
        NamedParameterJdbcTemplate tpl = getNamedParameterJdbcTemplate(dsName);
        return tpl.update(sql, params);
    }

    /**
     * 执行 INSERT 并返回自增主键
     */
    public Number insertAndReturnKey(String dsName, String sql, List<Object> params) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        Connection connection = DataSourceUtils.getConnection(ds);
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return (Number) rs.getObject(1);
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("执行 INSERT 并返回主键失败: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    /**
     * 批量更新
     */
    public int[] batchUpdate(String dsName, String sql, Map<String, Object>[] batchParams) {
        NamedParameterJdbcTemplate tpl = getNamedParameterJdbcTemplate(dsName);
        return tpl.batchUpdate(sql, batchParams);
    }

    // ========== PreparedStatement 直接执行 ==========

    /**
     * 使用 PreparedStatement 执行查询（回调处理 ResultSet）
     */
    public <T> T executeWithPs(String dsName, String sql, List<Object> params, ResultSetCallback<T> callback) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        Connection connection = DataSourceUtils.getConnection(ds);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                return callback.process(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("执行 SQL 失败: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    /**
     * 使用 PreparedStatement 执行 UPDATE/INSERT/DELETE
     */
    public int executeUpdateWithPs(String dsName, String sql, List<Object> params) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        Connection connection = DataSourceUtils.getConnection(ds);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("执行 SQL 失败: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    // ========== DDL 执行 ==========

    /**
     * 使用 Statement 执行 DDL（如 CREATE TABLE, DROP TABLE 等）
     */
    public void executeDdl(String dsName, String ddl) {
        DataSource ds = dataSourceManager.getDataSource(dsName);
        Connection connection = DataSourceUtils.getConnection(ds);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(ddl);
            log.info("DDL 执行成功: {}", ddl);
        } catch (SQLException e) {
            throw new RuntimeException("执行 DDL 失败: " + ddl, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    // ========== 原始 SQL 执行（兼容老代码） ==========

    /**
     * 执行原始 SQL（兼容 PubJdbcComponent/JdbcComponent.execSql）
     *
     * <p>支持 SELECT/INSERT/UPDATE/DELETE/COUNT，处理 CLOB/NCLOB 类型。
     *
     * @param dsName 数据源名称
     * @param sql    SQL 语句
     * @param type   操作类型
     * @return 查询结果列表（SELECT/COUNT）或影响行数（INSERT/UPDATE/DELETE）
     */
    public List<Map<String, Object>> execSql(String dsName, String sql, OperationType type) {
        try (Connection pooledConnection = getDataSourceConnection(dsName);
             PreparedStatement statement = pooledConnection.prepareStatement(sql)) {
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
                    ResultSet finalRs = rs;
                    columns.forEach(t -> {
                        try {
                            String name = t.get("name").toString();
                            Object value = finalRs.getObject(name);
                            // 处理 CLOB/NCLOB
                            if (value instanceof NClob) {
                                NClob nclob = finalRs.getNClob(name);
                                value = nclob.getSubString(1, (int) nclob.length());
                            } else if (value instanceof Clob) {
                                Clob clob = finalRs.getClob(name);
                                value = clob.getSubString(1, (int) clob.length());
                            }
                            // 修复 pg、达梦数据库获取行数错误问题
                            if ("count(0)".equals(name) || "COUNT(0)".equals(name)) {
                                name = "count";
                            }
                            item.put(name, value);
                        } catch (SQLException e) {
                            log.error("读取结果集异常", e);
                        }
                    });
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
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            log.error("执行JDBC查询时出现异常", e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 调用存储过程
     *
     * @param dsName 数据源名称
     * @param sql    存储过程调用 SQL
     * @return 输出参数结果（retCode, retMsg）
     */
    public Map<String, Object> execProcedure(String dsName, String sql) throws Exception {
        Map<String, Object> result = new HashMap<>();
        try (Connection pooledConnection = getDataSourceConnection(dsName);
             CallableStatement cstmt = pooledConnection.prepareCall(sql)) {
            cstmt.registerOutParameter(1, Types.VARCHAR);
            cstmt.registerOutParameter(2, Types.VARCHAR);
            log.info("执行存储过程SQL: {}", sql);
            cstmt.execute();
            result.put("retCode", cstmt.getString(1));
            result.put("retMsg", cstmt.getString(2));
            return result;
        }
    }

    // ========== 数据库方言相关方法 ==========

    /**
     * 生成分页 SQL
     *
     * @param dsName     数据源名称
     * @param querySelect 原始查询 SQL
     * @param pageIndex  页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页 SQL
     */
    public String handlePageSql(String dsName, String querySelect, int pageIndex, int pageSize) {
        String dbType = dataSourceManager.getDbType(dsName);
        return SqlUtils.buildPageSql(dbType, querySelect, pageIndex, pageSize);
    }

    /**
     * SQL 注入检测
     *
     * @param sqlStr SQL 字符串
     * @return true 表示存在注入风险
     */
    public boolean checkSqlInject(String sqlStr) {
        return SqlUtils.checkSqlInject(sqlStr);
    }

    /**
     * 获取数据库方言
     *
     * @param dsName 数据源名称
     * @return 数据库方言实现
     */
    public DbDialect getDialect(String dsName) {
        String dbType = dataSourceManager.getDbType(dsName);
        return dbDialectRegistry.getDialect(dbType);
    }

    // ========== 数据库元信息方法 ==========

    /**
     * 获取指定数据源的表列表
     */
    public List<Map<String, Object>> getTableList(String dsName) {
        String dbType = dataSourceManager.getDbType(dsName);
        String dbName = dataSourceManager.getDescriptor(dsName).getDbName();
        DbDialect dialect = dbDialectRegistry.getDialect(dbType);
        String sql = dialect.getTableListSql(dbName);
        return queryForList(dsName, sql);
    }

    /**
     * 获取指定表的列信息
     */
    public List<Map<String, Object>> getTableColumns(String dsName, String tableName) {
        String dbType = dataSourceManager.getDbType(dsName);
        String dbName = dataSourceManager.getDescriptor(dsName).getDbName();
        DbDialect dialect = dbDialectRegistry.getDialect(dbType);
        String sql = dialect.getTableColInfoSql(dbName, tableName);
        return queryForList(dsName, sql);
    }

    // ========== 存储过程辅助方法 ==========

    /**
     * 根据数据库类型生成存储过程调用 SQL
     *
     * @param dbType   数据库类型
     * @param procName 存储过程名称
     * @param eodDate  日期参数
     * @return 存储过程调用 SQL
     */
    public static String getProcSql(String dbType, String procName, String eodDate) {
        String sql;
        if ("oracle".equalsIgnoreCase(dbType)) {
            sql = "call " + procName + " ('" + eodDate + "', ?, ?)";
        } else if ("mysql".equalsIgnoreCase(dbType)) {
            sql = "call " + procName + " ('" + eodDate + "', ?, ?)";
        } else if ("postgresql".equalsIgnoreCase(dbType)) {
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

    // ========== 内部工具方法 ==========

    /**
     * 获取数据源连接
     */
    private Connection getDataSourceConnection(String dsName) throws SQLException {
        DruidDataSource ds = dataSourceManager.getDataSource(dsName);
        return ds.getConnection();
    }

    /**
     * 将驼峰命名转换为下划线命名
     */
    public static String camelToUnderscore(String camel) {
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(camel.charAt(0)));
        for (int i = 1; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (Character.isUpperCase(ch)) {
                result.append('_').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * 将下划线命名转换为驼峰命名
     */
    public static String underscoreToCamel(String underscore) {
        if (underscore == null || underscore.isEmpty()) {
            return underscore;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < underscore.length(); i++) {
            char ch = underscore.charAt(i);
            if (ch == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(ch));
                nextUpper = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    // ========== MyBatis 动态多数据源方法 ==========

    // 无返回值时直接用 executeInDataSource(dsName, session -> { ...; return null; })

    /**
     * 在指定数据源上执行 MyBatis 操作（Lambda 回调，有返回值）
     *
     * <p>使用自动提交模式，适合查询操作。需要事务时使用
     * {@link #executeInDataSourceTransactional}。
     *
     * @param dsName 数据源名称
     * @param action SqlSession 回调
     * @param <T>    返回类型
     * @return 执行结果
     */
    public <T> T executeInDataSource(String dsName, SqlSessionCallback<T> action) {
        SqlSessionFactory factory = getSqlSessionFactory(dsName);
        try (SqlSession session = factory.openSession(true)) {
            return action.doInSqlSession(session);
        }
    }

    // 解决 void 版本和泛型版本的方法签名冲突：删除重复的 void 版本
    // 无返回值时直接用 executeInDataSource(dsName, session -> { ...; return null; })

    /**
     * 在指定数据源上执行带事务的 MyBatis 操作
     *
     * <p>关闭自动提交，异常时自动回滚，正常时自动提交。
     *
     * @param dsName 数据源名称
     * @param action SqlSession 回调
     * @param <T>    返回类型
     * @return 执行结果
     */
    public <T> T executeInDataSourceTransactional(String dsName, SqlSessionCallback<T> action) {
        SqlSessionFactory factory = getSqlSessionFactory(dsName);
        SqlSession session = factory.openSession(false);
        try {
            T result = action.doInSqlSession(session);
            session.commit();
            return result;
        } catch (Exception e) {
            session.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * 在指定数据源上执行 Mapper 操作（Lambda 回调，类型安全）
     *
     * <p>推荐用法：调用方无需了解 SqlSession API，直接使用 Mapper 代理。
     *
     * <pre>
     * User user = tpl.executeWithMapper("business_db", UserMapper.class,
     *     mapper -> mapper.selectById(123));
     * </pre>
     *
     * @param dsName      数据源名称
     * @param mapperClass Mapper 接口类
     * @param callback    Mapper 回调
     * @param <M>         Mapper 类型
     * @param <T>         返回类型
     * @return 执行结果
     */
    public <M, T> T executeWithMapper(String dsName, Class<M> mapperClass, MapperCallback<M, T> callback) {
        return executeInDataSource(dsName, session -> {
            M mapper = session.getMapper(mapperClass);
            return callback.doWithMapper(mapper);
        });
    }

    /**
     * 在指定数据源上获取 Mapper 代理
     *
     * <p>注意：返回的 Mapper 代理绑定到一个已关闭的 SqlSession，
     * 仅适用于 MyBatis 的 Mapper 代理机制（延迟执行）。
     * 建议优先使用 {@link #executeWithMapper}。
     *
     * @param dsName      数据源名称
     * @param mapperClass Mapper 接口类
     * @param <M>         Mapper 类型
     * @return Mapper 代理
     */
    public <M> M getMapperInDataSource(String dsName, Class<M> mapperClass) {
        SqlSessionFactory factory = getSqlSessionFactory(dsName);
        SqlSession session = factory.openSession(true);
        try {
            return session.getMapper(mapperClass);
        } finally {
            session.close();
        }
    }

    /**
     * 在指定数据源上查询列表（statement ID）
     *
     * @param dsName       数据源名称
     * @param statementId  MyBatis statement ID（namespace.methodName）
     * @param params       参数
     * @param <T>          返回类型
     * @return 查询结果列表
     */
    public <T> List<T> selectListInDataSource(String dsName, String statementId, Object params) {
        return executeInDataSource(dsName, session -> session.selectList(statementId, params));
    }

    /**
     * 在指定数据源上查询单条（statement ID）
     *
     * @param dsName       数据源名称
     * @param statementId  MyBatis statement ID（namespace.methodName）
     * @param params       参数
     * @param <T>          返回类型
     * @return 查询结果
     */
    public <T> T selectOneInDataSource(String dsName, String statementId, Object params) {
        return executeInDataSource(dsName, session -> session.selectOne(statementId, params));
    }

    /**
     * 在指定数据源上插入/更新/删除（statement ID）
     *
     * @param dsName       数据源名称
     * @param statementId  MyBatis statement ID（namespace.methodName）
     * @param params       参数
     * @return 影响行数
     */
    public int updateInDataSource(String dsName, String statementId, Object params) {
        return executeInDataSource(dsName, session -> session.update(statementId, params));
    }

    /**
     * 获取动态数据源的 SqlSessionFactory
     */
    private SqlSessionFactory getSqlSessionFactory(String dsName) {
        if (sqlSessionFactoryManager == null) {
            throw new IllegalStateException("DynamicSqlSessionFactoryManager 未注入，"
                    + "请在 DynamicDataSourceAutoConfiguration 中配置");
        }
        return sqlSessionFactoryManager.getSqlSessionFactory(dsName);
    }

    /**
     * ResultSet 回调接口
     */
    @FunctionalInterface
    public interface ResultSetCallback<T> {
        T process(ResultSet rs) throws SQLException;
    }
}
