package io.github.openground.common.jdbc;

import java.util.regex.Pattern;

/**
 * SQL 工具类
 *
 * <p>提供分页 SQL 生成和 SQL 注入检测能力，
 * 合并自 imap-pub PubJdbcComponent 和 ground-auth IDataSourceService 的相关逻辑。
 *
 * @author open-ground
 * @since 1.0.2
 */
public final class SqlUtils {

    /** SQL 注入检测正则 */
    private static final Pattern SQL_INJECT_PATTERN = Pattern.compile(
            "\\b(insert|delete|update|drop|truncate|alter|create|exec|exists|xp_cmdshell|sp_)\\b|" +
            "\\b(or|and)\\s+[\\d\\w]=[\\d\\w]",
            Pattern.CASE_INSENSITIVE
    );

    private SqlUtils() {
    }

    /**
     * 根据数据库类型生成分页 SQL
     *
     * @param dbType      数据库类型（mysql/oracle/postgresql/gaussdb 等）
     * @param querySelect 原始查询 SQL
     * @param pageIndex   页码（从 1 开始）
     * @param pageSize    每页条数
     * @return 分页 SQL
     */
    public static String buildPageSql(String dbType, String querySelect, int pageIndex, int pageSize) {
        int offset = (pageIndex - 1) * pageSize;
        int end = offset + pageSize;
        switch (dbType) {
            case DbTypeDetector.ORACLE:
                return "SELECT * FROM (SELECT tmp_tb.*, ROWNUM FROM (" + querySelect
                        + ") tmp_tb) WHERE ROWNUM >= " + offset + " AND ROWNUM < " + end;
            case DbTypeDetector.POSTGRESQL:
            case DbTypeDetector.GAUSSDB:
                return querySelect + " LIMIT " + pageSize + " OFFSET " + offset;
            case DbTypeDetector.MYSQL:
            case DbTypeDetector.DM:
            default:
                return querySelect + " LIMIT " + offset + ", " + pageSize;
        }
    }

    /**
     * 生成分页 SQL（使用 offset 和 limit）
     *
     * @param dbType      数据库类型
     * @param querySelect 原始查询 SQL
     * @param offset      偏移量
     * @param limit       条数
     * @return 分页 SQL
     */
    public static String buildPageSqlByOffset(String dbType, String querySelect, int offset, int limit) {
        switch (dbType) {
            case DbTypeDetector.ORACLE:
                return "SELECT * FROM (SELECT tmp_tb.*, ROWNUM FROM (" + querySelect
                        + ") tmp_tb) WHERE ROWNUM >= " + offset + " AND ROWNUM < " + (offset + limit);
            case DbTypeDetector.POSTGRESQL:
            case DbTypeDetector.GAUSSDB:
                return querySelect + " LIMIT " + limit + " OFFSET " + offset;
            case DbTypeDetector.MYSQL:
            case DbTypeDetector.DM:
            default:
                return querySelect + " LIMIT " + offset + ", " + limit;
        }
    }

    /**
     * SQL 注入检测
     *
     * <p>检测 SQL 字符串中是否包含常见的注入关键字。
     * 注意：此方法仅用于辅助检测，不能替代参数化查询。
     *
     * @param sqlStr SQL 字符串
     * @return true 表示存在注入风险
     */
    public static boolean checkSqlInject(String sqlStr) {
        if (sqlStr == null || sqlStr.isEmpty()) {
            return false;
        }
        String sql = sqlStr.replaceAll("\\s+", "");
        return SQL_INJECT_PATTERN.matcher(sql).find();
    }
}
