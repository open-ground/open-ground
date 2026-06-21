package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库元数据提取器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class MetadataExtractor {

    private final DatabaseTypeDetector databaseTypeDetector;

    public MetadataExtractor(DatabaseTypeDetector databaseTypeDetector) {
        this.databaseTypeDetector = databaseTypeDetector;
    }

    /**
     * 提取数据库中指定表的元数据
     *
     * @param conn       JDBC 连接
     * @param tableNames 表名列表（为空则提取所有表）
     * @return 表信息 Map（key=表名）
     */
    public Map<String, DbTableInfo> extractTableMetadata(Connection conn, List<String> tableNames) {
        Map<String, DbTableInfo> tables = new HashMap<>();
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String dbType = databaseTypeDetector.detect(conn);
            String catalog = conn.getCatalog();
            String schema = getSchema(conn, dbType);

            try (ResultSet rs = meta.getTables(catalog, schema, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME").toLowerCase();
                    if (!tableNames.isEmpty() && !tableNames.contains(tableName)) {
                        continue;
                    }
                    if (tableName.contains(" ") || tableName.startsWith("BIN$")) {
                        continue;
                    }
                    DbTableInfo info = new DbTableInfo();
                    info.setTableName(tableName);
                    info.setComment(rs.getString("REMARKS"));
                    info.setColumns(extractColumns(meta, catalog, schema, tableName));
                    tables.put(tableName, info);
                }
            }
        } catch (SQLException e) {
            log.error("提取表元数据失败", e);
        }
        return tables;
    }

    /**
     * 提取表中所有数据
     *
     * @param conn      JDBC 连接
     * @param tableName 表名
     * @return 数据行列表
     */
    public List<Map<String, Object>> extractTableData(Connection conn, String tableName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE del_flag = '0'")) {
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            log.error("提取表数据失败: {}", tableName, e);
        }
        return rows;
    }

    /**
     * 提取列信息
     */
    private List<DbColumnInfo> extractColumns(DatabaseMetaData meta, String catalog,
                                               String schema, String tableName) throws SQLException {
        List<DbColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                DbColumnInfo col = new DbColumnInfo();
                col.setColumnName(rs.getString("COLUMN_NAME").toLowerCase());
                col.setDataType(rs.getInt("DATA_TYPE"));
                col.setTypeName(rs.getString("TYPE_NAME"));
                col.setColumnSize(rs.getInt("COLUMN_SIZE"));
                col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                col.setComment(rs.getString("REMARKS"));
                col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                columns.add(col);
            }
        }
        return columns;
    }

    /**
     * 获取 schema 名称
     */
    private String getSchema(Connection conn, String dbType) {
        try {
            switch (dbType) {
                case "mysql":
                    return conn.getCatalog();
                case "oracle":
                    return conn.getSchema();
                case "dm":
                    return conn.getSchema();
                case "postgresql":
                case "gaussdb":
                    return "public";
                default:
                    return conn.getSchema();
            }
        } catch (SQLException e) {
            log.warn("获取 schema 失败", e);
            return null;
        }
    }

    // ===== 内部类 =====

    public static class DbTableInfo {
        private String tableName;
        private List<DbColumnInfo> columns;
        private String comment;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<DbColumnInfo> getColumns() { return columns; }
        public void setColumns(List<DbColumnInfo> columns) { this.columns = columns; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class DbColumnInfo {
        private String columnName;
        private int dataType;
        private String typeName;
        private int columnSize;
        private boolean nullable;
        private String defaultValue;
        private String comment;
        private int ordinalPosition;

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public int getDataType() { return dataType; }
        public void setDataType(int dataType) { this.dataType = dataType; }
        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }
        public int getColumnSize() { return columnSize; }
        public void setColumnSize(int columnSize) { this.columnSize = columnSize; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public int getOrdinalPosition() { return ordinalPosition; }
        public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }
    }
}
