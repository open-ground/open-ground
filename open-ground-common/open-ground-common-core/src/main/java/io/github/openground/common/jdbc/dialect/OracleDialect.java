package io.github.openground.common.jdbc.dialect;

import io.github.openground.common.jdbc.DbTypeDetector;
import org.springframework.stereotype.Component;

/**
 * Oracle 方言实现
 *
 * @author open-ground
 * @since 1.0.2
 */
@Component
public class OracleDialect implements DbDialect {

    @Override
    public String getDbType() {
        return DbTypeDetector.ORACLE;
    }

    @Override
    public String buildPageSql(String querySelect, int offset, int limit) {
        return "SELECT * FROM (SELECT tmp_tb.*, ROWNUM FROM (" + querySelect
                + ") tmp_tb) WHERE ROWNUM >= " + offset + " AND ROWNUM < " + (offset + limit);
    }

    @Override
    public String getTableListSql(String dbName) {
        return "SELECT TABLE_NAME, TABLE_TYPE, COMMENTS AS TABLE_COMMENT FROM ALL_TAB_COMMENTS "
                + "WHERE OWNER = '" + dbName + "' AND TABLE_NAME IS NOT NULL "
                + "AND TABLE_NAME NOT LIKE 'BIN$%' ORDER BY TABLE_NAME";
    }

    @Override
    public String getTableColInfoSql(String dbName, String tableName) {
        return "SELECT A.COLUMN_ID ORDINAL_POSITION, A.COLUMN_NAME, E.COMMENTS COLUMN_COMMENT, "
                + "A.DATA_TYPE, A.DATA_LENGTH CHARACTER_MAXIMUM_LENGTH, "
                + "A.DATA_PRECISION NUMERIC_PRECISION, A.DATA_SCALE NUMERIC_SCALE, "
                + "A.NULLABLE IS_NULLABLE, "
                + "CASE WHEN D.COLUMN_NAME IS NOT NULL THEN 'PRI' END COLUMN_KEY "
                + "FROM ALL_TAB_COLUMNS A "
                + "LEFT JOIN ALL_COL_COMMENTS E ON A.OWNER = UPPER(E.OWNER) "
                + "AND A.TABLE_NAME = E.TABLE_NAME AND A.COLUMN_NAME = E.COLUMN_NAME "
                + "LEFT JOIN (SELECT DISTINCT COLUMN_NAME FROM ALL_CONS_COLUMNS "
                + "WHERE OWNER = '" + dbName + "' AND TABLE_NAME = '" + tableName + "') D "
                + "ON A.COLUMN_NAME = D.COLUMN_NAME "
                + "WHERE A.OWNER = '" + dbName.toUpperCase() + "' AND A.TABLE_NAME = '" + tableName + "'";
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
            case "nclob":
            case "clob":
            case "blob":
                break;
            case "date":
            case "datetime":
            case "timestamp":
                value = "TO_DATE('" + value + "', 'SYYYY-MM-DD HH24:MI:SS')";
                break;
            case "varchar":
            case "varchar2":
            case "text":
            default:
                value = "'" + value + "'";
        }
        return value;
    }
}
