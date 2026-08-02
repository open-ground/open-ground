package io.github.openground.common.jdbc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 工具类
 *
 * <p>提供分页 SQL 生成、SQL 注入检测、数据库类型值转换等能力，
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

    // ==================== 数据库类型分组（跨数据库兼容） ====================

    /** 整数类型 */
    private static final Set<String> INTEGER_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Standard SQL & MySQL
            "int", "integer", "bigint", "smallint", "tinyint", "mediumint", "year",
            // PostgreSQL / GaussDB
            "int2", "int4", "int8", "serial", "bigserial", "smallserial"
    )));

    /** 浮点类型 */
    private static final Set<String> FLOAT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Standard SQL & MySQL
            "float", "double", "real", "double precision",
            // PostgreSQL / GaussDB
            "float4", "float8",
            // Oracle / DM
            "binary_float", "binary_double"
    )));

    /** 高精度十进制类型 */
    private static final Set<String> DECIMAL_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "decimal", "numeric", "money"
    )));

    /** 布尔类型 */
    private static final Set<String> BOOLEAN_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "boolean", "bool"
    )));

    /** 时间戳类型 */
    private static final Set<String> TIMESTAMP_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "timestamp", "timestamptz", "datetime", "smalldatetime", "datetime2"
    )));

    /** 时间类型 */
    private static final Set<String> TIME_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "time", "timetz"
    )));

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

    // ==================== 数据库类型值转换 ====================

    /**
     * 规整数据库类型名，统一为可匹配的标准形式
     * <ul>
     *   <li>varchar(255) → varchar</li>
     *   <li>decimal(10,2) → decimal</li>
     *   <li>int unsigned → int</li>
     *   <li>timestamp(6) with time zone → timestamp</li>
     *   <li>timestamp with local time zone → timestamp</li>
     * </ul>
     *
     * @param typeName 数据库原始类型名
     * @return 规整后的类型名（小写）
     */
    public static String normalizeSqlType(String typeName) {
        if (typeName == null) return "";
        String t = typeName.trim().toLowerCase();
        // 去掉大小信息: varchar(255) → varchar, decimal(10,2) → decimal
        int parenIdx = t.indexOf('(');
        if (parenIdx >= 0) {
            t = t.substring(0, parenIdx).trim();
        }
        // 去掉 MySQL unsigned / zerofill 后缀
        t = t.replaceAll("\\s+unsigned\\b", "")
                .replaceAll("\\s+zerofill\\b", "")
                .trim();
        // 去掉 Oracle/PostgreSQL 时区后缀
        t = t.replaceAll("\\s+with\\s+(local\\s+)?time\\s+zone$", "")
                .replaceAll("\\s+without\\s+time\\s+zone$", "")
                .trim();
        // 去掉 MySQL character set / collate 后缀
        t = t.replaceAll("\\s+character\\s+set\\s+\\w+", "")
                .replaceAll("\\s+collate\\s+\\w+", "")
                .trim();
        return t;
    }

    /**
     * 将值转换为目标数据库列类型对应的 Java 类型
     *
     * <p>支持整数、浮点、十进制、布尔、日期、时间戳、时间等类型，
     * 覆盖 MySQL/Oracle/PostgreSQL/DM/GaussDB 等常见数据库的类型名。
     *
     * @param value      原始值（String 或 Number 等）
     * @param targetType 目标数据库类型名（原始形式，内部会调用 {@link #normalizeSqlType} 规整）
     * @return 转换后的 Java 对象，null 输入返回 null，空字符串返回 null
     * @throws IllegalArgumentException 转换失败时抛出
     */
    public static Object convertValue(Object value, String targetType) {
        if (value == null) return null;
        if (targetType == null) return value;

        String strVal = value.toString().trim();
        if (strVal.isEmpty()) return null;

        String normalized = normalizeSqlType(targetType);

        // Oracle NUMBER: 无法从类型名区分整数与小数，先尝试整数再回退 BigDecimal
        if ("number".equals(normalized)) {
            try {
                return Long.parseLong(strVal);
            } catch (NumberFormatException e) {
                try {
                    return new BigDecimal(strVal);
                } catch (NumberFormatException e2) {
                    throw new IllegalArgumentException(
                            "数值转换失败: 值='" + value + "', 目标类型=" + targetType, e2);
                }
            }
        }

        // 整数类型
        if (INTEGER_TYPES.contains(normalized)) {
            if (value instanceof Number) return ((Number) value).longValue();
            try {
                return Long.parseLong(strVal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "数值转换失败: 值='" + value + "', 目标类型=" + targetType, e);
            }
        }

        // 浮点类型
        if (FLOAT_TYPES.contains(normalized)) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            try {
                return Double.parseDouble(strVal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "数值转换失败: 值='" + value + "', 目标类型=" + targetType, e);
            }
        }

        // 高精度十进制类型 → BigDecimal 避免精度丢失
        if (DECIMAL_TYPES.contains(normalized)) {
            try {
                return new BigDecimal(strVal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "数值转换失败: 值='" + value + "', 目标类型=" + targetType, e);
            }
        }

        // 布尔类型
        if (BOOLEAN_TYPES.contains(normalized)) {
            return parseBooleanValue(strVal);
        }

        // 日期类型
        if ("date".equals(normalized)) {
            if (value instanceof java.sql.Date) return value;
            if (value instanceof Date) return new java.sql.Date(((Date) value).getTime());
            return parseSqlDate(strVal);
        }

        // 时间戳类型
        if (TIMESTAMP_TYPES.contains(normalized)) {
            if (value instanceof Timestamp) return value;
            if (value instanceof Date) return new Timestamp(((Date) value).getTime());
            return parseSqlTimestamp(strVal);
        }

        // 时间类型
        if (TIME_TYPES.contains(normalized)) {
            return parseSqlTime(strVal);
        }

        // 默认：字符串类型（varchar, char, text, clob, json, uuid, bytea, blob 等）
        return strVal;
    }

    /**
     * 布尔值转换，兼容 PostgreSQL (t/f)、MySQL (1/0)、常见文本 (true/false/yes/no)
     *
     * @param val 字符串值
     * @return Boolean 值
     * @throws IllegalArgumentException 无法识别为布尔值时抛出
     */
    public static Boolean parseBooleanValue(String val) {
        if ("true".equalsIgnoreCase(val) || "t".equalsIgnoreCase(val)
                || "1".equals(val) || "yes".equalsIgnoreCase(val)
                || "y".equalsIgnoreCase(val)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(val) || "f".equalsIgnoreCase(val)
                || "0".equals(val) || "no".equalsIgnoreCase(val)
                || "n".equalsIgnoreCase(val)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("布尔值转换失败: 值='" + val + "'");
    }

    /**
     * 尝试常见日期格式解析为 {@link java.sql.Date}
     *
     * @param val 日期字符串
     * @return java.sql.Date
     * @throws IllegalArgumentException 所有格式解析失败时抛出
     */
    public static java.sql.Date parseSqlDate(String val) {
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy-MM", "yyyyMM"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern).parse(val);
                return new java.sql.Date(d.getTime());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("日期转换失败: 值='" + val + "'，支持的格式:"
                + " yyyy-MM-dd, yyyy/MM/dd, yyyyMMdd");
    }

    /**
     * 尝试常见时间戳格式解析为 {@link java.sql.Timestamp}
     *
     * @param val 时间戳字符串
     * @return java.sql.Timestamp
     * @throws IllegalArgumentException 所有格式解析失败时抛出
     */
    public static java.sql.Timestamp parseSqlTimestamp(String val) {
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyyMMddHHmmss",
                "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern).parse(val);
                return new Timestamp(d.getTime());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("时间戳转换失败: 值='" + val + "'，支持的格式:"
                + " yyyy-MM-dd HH:mm:ss, yyyyMMddHHmmss");
    }

    /**
     * 尝试常见时间格式解析为 {@link java.sql.Time}
     *
     * @param val 时间字符串
     * @return java.sql.Time
     * @throws IllegalArgumentException 所有格式解析失败时抛出
     */
    public static java.sql.Time parseSqlTime(String val) {
        String[] patterns = {"HH:mm:ss", "HH:mm:ss.SSS", "HH:mm"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern).parse(val);
                return new java.sql.Time(d.getTime());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("时间转换失败: 值='" + val + "'，支持的格式:"
                + " HH:mm:ss, HH:mm:ss.SSS, HH:mm");
    }
}
