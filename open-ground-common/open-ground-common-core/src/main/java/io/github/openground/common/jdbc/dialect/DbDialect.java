package io.github.openground.common.jdbc.dialect;

/**
 * 数据库方言适配器 SPI
 *
 * <p>替代原 ground-auth 的 IDataSourceService。
 * 不同数据库实现此接口，通过 Spring Bean 自动注册到 {@link DbDialectRegistry}。
 *
 * <p>提供分页 SQL 生成、表列表 SQL、表字段 SQL、数据库类型标识、列值格式化能力。
 *
 * @author open-ground
 * @since 1.0.2
 */
public interface DbDialect {

    /**
     * 获取数据库类型标识
     *
     * @return 数据库类型（mysql/oracle/postgresql/dm/gaussdb 等）
     */
    String getDbType();

    /**
     * 生成分页 SQL
     *
     * @param querySelect 原始查询 SQL
     * @param offset      偏移量（从 0 开始）
     * @param limit       条数
     * @return 分页 SQL
     */
    String buildPageSql(String querySelect, int offset, int limit);

    /**
     * 获取表列表 SQL
     *
     * @param dbName 数据库名/schema
     * @return 查询表列表的 SQL
     */
    String getTableListSql(String dbName);

    /**
     * 获取表字段信息 SQL
     *
     * @param dbName    数据库名/schema
     * @param tableName 表名
     * @return 查询表字段信息的 SQL
     */
    String getTableColInfoSql(String dbName, String tableName);

    /**
     * 列值格式化（字符加引号、日期用函数等）
     *
     * @param colType 列类型
     * @param value   原始值
     * @return 格式化后的值
     */
    Object formatColValue(String colType, Object value);
}
