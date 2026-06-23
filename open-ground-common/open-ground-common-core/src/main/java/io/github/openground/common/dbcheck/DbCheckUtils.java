package io.github.openground.common.dbcheck;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库检查工具类
 * <p>提供数据库标识符引用、类型方言映射等静态工具方法，统一多数据库兼容逻辑。
 *
 * @author open-ground
 * @since 2026-06-18
 */
public final class DbCheckUtils {

    private DbCheckUtils() {}

    /**
     * 数据库标识符引用（表名/列名）
     * <ul>
     *   <li>MySQL → {@code `name`}</li>
     *   <li>Oracle / DM → {@code NAME}（大写、无引号）</li>
     *   <li>PostgreSQL → {@code "name"}（双引号、保留大小写）</li>
     * </ul>
     */
    public static String quoteId(String name, String dbType) {
        if (name == null) return null;
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return "\"" + name + "\"";
        }
        if ("oracle".equalsIgnoreCase(dbType) || "dm".equalsIgnoreCase(dbType)) {
            return name.toUpperCase();
        }
        // MySQL 及默认
        return "`" + name + "`";
    }

    /**
     * 判断是否为 Oracle 或达梦
     */
    public static boolean isOracleOrDm(String dbType) {
        return "oracle".equalsIgnoreCase(dbType) || "dm".equalsIgnoreCase(dbType);
    }

    /**
     * 判断是否为 PostgreSQL
     */
    public static boolean isPostgresql(String dbType) {
        return "postgresql".equalsIgnoreCase(dbType);
    }

    /**
     * 判断是否为 MySQL
     */
    public static boolean isMysql(String dbType) {
        return "mysql".equalsIgnoreCase(dbType);
    }

    /**
     * 将脚本中的列类型映射到目标数据库方言
     * <p>例如 VARCHAR2 → VARCHAR、NUMBER → NUMERIC
     */
    public static String mapColumnType(String scriptType, String dbType) {
        if (scriptType == null) return "VARCHAR";
        String upper = scriptType.trim().toUpperCase();

        // Oracle/DM 专有类型 → 通用类型
        if ("VARCHAR2".equals(upper)) return "VARCHAR";
        if ("NVARCHAR2".equals(upper)) return "VARCHAR";
        if ("NUMBER".equals(upper)) {
            // MySQL/PostgreSQL 用 NUMERIC/DECIMAL，Oracle 保留 NUMBER
            return isOracleOrDm(dbType) ? "NUMBER" : "NUMERIC";
        }
        if ("NCHAR".equals(upper)) return "CHAR";
        if ("CLOB".equals(upper)) return "TEXT";
        if ("BLOB".equals(upper)) return "BLOB";

        // PostgreSQL 专有类型 → 通用类型
        if ("SERIAL".equals(upper) || "SMALLSERIAL".equals(upper)) return "INT";
        if ("BIGSERIAL".equals(upper)) return "BIGINT";
        if ("JSONB".equals(upper)) return "JSON";
        if ("BYTEA".equals(upper)) return "BLOB";
        if ("TIMESTAMPTZ".equals(upper)) return "TIMESTAMP";
        if ("FLOAT4".equals(upper)) return "FLOAT";
        if ("FLOAT8".equals(upper)) return "DOUBLE";

        // MySQL 专有类型 → 通用类型
        if ("MEDIUMINT".equals(upper)) return "INT";
        if ("YEAR".equals(upper)) return "INT";
        if ("ENUM".equals(upper) || "SET".equals(upper)) return "VARCHAR";

        // 已有通用类型直接返回
        return scriptType.trim();
    }

    /**
     * 提取类型的基类型（去掉括号和长度），用于类型比较
     */
    public static String baseType(String raw) {
        if (raw == null) return "";
        String t = raw.toUpperCase().trim();
        int paren = t.indexOf('(');
        if (paren > 0) t = t.substring(0, paren);
        return t.trim();
    }

    /**
     * 类型名称归一化映射表（用于 DbSchemaComparator 的 compare）
     */
    private static final Map<String, String> TYPE_NORMALIZE_MAP = new HashMap<>();
    static {
        // Oracle/DM
        TYPE_NORMALIZE_MAP.put("VARCHAR2", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("NVARCHAR2", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("NCHAR", "CHAR");
        TYPE_NORMALIZE_MAP.put("NUMBER", "NUMERIC");
        TYPE_NORMALIZE_MAP.put("CLOB", "LONGTEXT");
        // PostgreSQL
        TYPE_NORMALIZE_MAP.put("SERIAL", "INT");
        TYPE_NORMALIZE_MAP.put("BIGSERIAL", "BIGINT");
        TYPE_NORMALIZE_MAP.put("SMALLSERIAL", "SMALLINT");
        TYPE_NORMALIZE_MAP.put("JSONB", "JSON");
        TYPE_NORMALIZE_MAP.put("UUID", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("BYTEA", "BLOB");
        TYPE_NORMALIZE_MAP.put("TIMESTAMPTZ", "TIMESTAMP");
        TYPE_NORMALIZE_MAP.put("FLOAT4", "FLOAT");
        TYPE_NORMALIZE_MAP.put("FLOAT8", "DOUBLE");
        TYPE_NORMALIZE_MAP.put("REAL", "FLOAT");
        // MySQL
        TYPE_NORMALIZE_MAP.put("MEDIUMINT", "INT");
        TYPE_NORMALIZE_MAP.put("YEAR", "INT");
        TYPE_NORMALIZE_MAP.put("ENUM", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("SET", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("LONGTEXT", "LONGTEXT");
        // 标准同义词
        TYPE_NORMALIZE_MAP.put("INTEGER", "INT");
        TYPE_NORMALIZE_MAP.put("INT4", "INT");
        TYPE_NORMALIZE_MAP.put("CHARACTER VARYING", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("CHAR VARYING", "VARCHAR");
        TYPE_NORMALIZE_MAP.put("CHARACTER", "CHAR");
        TYPE_NORMALIZE_MAP.put("BOOL", "BIT");
        TYPE_NORMALIZE_MAP.put("BOOLEAN", "BIT");
        // 数据库驱动特定类型
        TYPE_NORMALIZE_MAP.put("BINARY_DOUBLE", "DOUBLE");
        TYPE_NORMALIZE_MAP.put("BINARY_FLOAT", "FLOAT");
    }

    /**
     * 归一化类型名（去掉括号，映射同义词）
     */
    public static String normalizeType(String raw) {
        String base = baseType(raw);
        return TYPE_NORMALIZE_MAP.getOrDefault(base, base);
    }
}
