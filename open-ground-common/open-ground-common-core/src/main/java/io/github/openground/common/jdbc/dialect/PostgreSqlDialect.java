package io.github.openground.common.jdbc.dialect;

import io.github.openground.common.jdbc.DbTypeDetector;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 方言实现
 *
 * @author open-ground
 * @since 1.0.2
 */
@Component
public class PostgreSqlDialect implements DbDialect {

    @Override
    public String getDbType() {
        return DbTypeDetector.POSTGRESQL;
    }

    @Override
    public String buildPageSql(String querySelect, int offset, int limit) {
        return querySelect + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String getTableListSql(String dbName) {
        if (StringUtils.isEmpty(dbName)) {
            dbName = "public";
        }
        return "SELECT t1.table_name AS TABLE_NAME, 'BASE TABLE' as TABLE_TYPE, "
                + "t2.description AS TABLE_COMMENT "
                + "FROM (SELECT table_name, oid FROM information_schema.tables a "
                + "LEFT JOIN (SELECT max(oid) oid, relname FROM pg_class GROUP BY relname) b "
                + "ON a.table_name = b.relname WHERE table_schema = '" + dbName + "') t1 "
                + "LEFT JOIN (SELECT * FROM pg_description WHERE objsubid = 0) t2 ON t1.oid = t2.objoid";
    }

    @Override
    public String getTableColInfoSql(String dbName, String tableName) {
        if (StringUtils.isEmpty(dbName)) {
            dbName = "public";
        }
        return "SELECT A.attnum AS ORDINAL_POSITION, A.attname AS COLUMN_NAME, "
                + "col_description(A.attrelid, A.attnum) AS COLUMN_COMMENT, "
                + "T.typname AS DATA_TYPE, d.character_maximum_length AS CHARACTER_MAXIMUM_LENGTH, "
                + "d.numeric_precision AS NUMERIC_PRECISION, d.numeric_scale AS NUMERIC_SCALE, "
                + "CASE WHEN A.attnotnull = 't' THEN 'NO' ELSE 'YES' END AS IS_NULLABLE, "
                + "CASE WHEN LENGTH(b.attname) > 0 THEN 'PRI' END AS COLUMN_KEY, "
                + "d.column_default COLUMN_DEFAULT "
                + "FROM pg_class C LEFT JOIN pg_namespace P ON C.relnamespace = P.oid, "
                + "pg_attribute A, pg_type T, information_schema.COLUMNS d "
                + "LEFT JOIN (SELECT pg_attribute.attname FROM pg_index, pg_class, pg_attribute "
                + "WHERE pg_class.oid = '" + tableName + "' :: regclass "
                + "AND pg_index.indrelid = pg_class.oid "
                + "AND pg_attribute.attrelid = pg_class.oid "
                + "AND pg_attribute.attnum = ANY(pg_index.indkey)) b ON d.COLUMN_NAME = b.attname "
                + "WHERE A.attrelid = C.oid AND A.atttypid = T.oid AND A.attnum > 0 "
                + "AND C.relname = d.TABLE_NAME AND d.COLUMN_NAME = A.attname "
                + "AND d.table_schema = P.nspname AND C.relname = '" + tableName + "' "
                + "AND d.table_schema = '" + dbName + "'";
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
            case "date":
            case "datetime":
            case "timestamp":
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
