package io.github.openground.common.jdbc.dialect;

import io.github.openground.common.jdbc.DbTypeDetector;
import org.springframework.stereotype.Component;

/**
 * MySQL 方言实现
 *
 * @author open-ground
 * @since 1.0.2
 */
@Component
public class MySqlDialect implements DbDialect {

    @Override
    public String getDbType() {
        return DbTypeDetector.MYSQL;
    }

    @Override
    public String buildPageSql(String querySelect, int offset, int limit) {
        return querySelect + " LIMIT " + offset + ", " + limit;
    }

    @Override
    public String getTableListSql(String dbName) {
        return "SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT FROM information_schema.TABLES "
                + "WHERE table_schema = '" + dbName + "' ORDER BY TABLE_NAME";
    }

    @Override
    public String getTableColInfoSql(String dbName, String tableName) {
        return "SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE, "
                + "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE, COLUMN_KEY "
                + "FROM information_schema.COLUMNS "
                + "WHERE table_schema = '" + dbName + "' AND TABLE_NAME = '" + tableName + "' "
                + "ORDER BY ORDINAL_POSITION";
    }

    @Override
    public Object formatColValue(String colType, Object value) {
        if (colType == null) {
            return "'" + value + "'";
        }
        switch (colType.toLowerCase()) {
            case "double":
            case "number":
            case "numeric":
            case "decimal":
            case "int":
            case "blob":
                break;
            case "date":
            case "datetime":
            case "timestamp":
            case "varchar":
            case "varchar2":
            case "text":
            default:
                value = "'" + value + "'";
        }
        return value;
    }
}
