package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单 SQL 解析器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class SimpleSqlParser {

    /** 匹配 CREATE TABLE 语句 */
    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`?\\w+`?\\.)?`?(\\w+)`?\\s*\\((.*?)\\)\\s*(?:ENGINE\\s*=\\s*\\w+.*?)?;",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 匹配列定义 */
    private static final Pattern COLUMN_PATTERN =
            Pattern.compile("`?(\\w+)`?\\s+(\\w+(?:\\(\\d+(?:,\\d+)?\\))?(?:\\s+UNSIGNED)?(?:\\s+IDENTITY)?(?:\\s+NOT\\s+NULL)?(?:\\s+DEFAULT\\s+[^,]+)?(?:\\s+AUTO_INCREMENT)?(?:\\s+NULL)?(?:\\s+PRIMARY\\s+KEY)?(?:\\s+COMMENT\\s+'[^']*')?)",
                    Pattern.CASE_INSENSITIVE);

    /** 匹配 INSERT INTO 语句 */
    private static final Pattern INSERT_PATTERN =
            Pattern.compile("INSERT\\s+INTO\\s+(?:`?\\w+`?\\.)?`?(\\w+)`?\\s*(?:\\((.*?)\\))?\\s*VALUES\\s*\\((.*?)\\)\\s*;",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * 解析 SQL 中的 CREATE TABLE 语句
     *
     * @param sql SQL 内容
     * @return 表定义列表
     */
    public List<TableDefinition> parseCreateTables(String sql) {
        List<TableDefinition> tables = new ArrayList<>();
        String cleaned = sql.replaceAll("--.*?\\n", "\n").replaceAll("/\\*.*?\\*/", "");
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            String body = matcher.group(2);
            TableDefinition def = new TableDefinition();
            def.setTableName(tableName.toLowerCase());
            def.setColumns(parseColumns(body));
            def.setOriginalSql(matcher.group());
            tables.add(def);
        }
        return tables;
    }

    /**
     * 解析列定义
     */
    private List<TableColumnDefinition> parseColumns(String body) {
        List<TableColumnDefinition> columns = new ArrayList<>();
        String[] parts = body.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || part.toUpperCase().startsWith("PRIMARY")
                    || part.toUpperCase().startsWith("KEY")
                    || part.toUpperCase().startsWith("INDEX")
                    || part.toUpperCase().startsWith("UNIQUE")
                    || part.toUpperCase().startsWith("CONSTRAINT")
                    || part.toUpperCase().startsWith("FOREIGN")
                    || part.toUpperCase().startsWith("CHECK")) {
                continue;
            }
            Matcher cm = COLUMN_PATTERN.matcher(part);
            if (cm.find()) {
                TableColumnDefinition col = new TableColumnDefinition();
                col.setColumnName(cm.group(1).toLowerCase());
                col.setColumnType(parseColumnType(cm.group(2)));
                col.setNullable(!part.toUpperCase().contains("NOT NULL"));
                col.setPrimaryKey(part.toUpperCase().contains("PRIMARY KEY"));
                col.setAutoIncrement(part.toUpperCase().contains("AUTO_INCREMENT"));
                columns.add(col);
            }
        }
        return columns;
    }

    /**
     * 解析列类型
     */
    private String parseColumnType(String typeDef) {
        String type = typeDef.trim();
        int firstSpace = type.indexOf(' ');
        if (firstSpace > 0) {
            type = type.substring(0, firstSpace);
        }
        return type.toUpperCase();
    }

    /**
     * 解析 INSERT 语句
     *
     * @param sql SQL 内容
     * @return INSERT 语句列表
     */
    public List<InsertStatement> parseInserts(String sql) {
        List<InsertStatement> inserts = new ArrayList<>();
        String cleaned = sql.replaceAll("--.*?\\n", "\n").replaceAll("/\\*.*?\\*/", "");
        Matcher matcher = INSERT_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            InsertStatement stmt = new InsertStatement();
            stmt.setTableName(matcher.group(1).toLowerCase());
            String columns = matcher.group(2);
            String values = matcher.group(3);
            if (columns != null) {
                stmt.setColumns(columns);
            }
            stmt.setValues(values);
            stmt.setOriginalSql(matcher.group());
            inserts.add(stmt);
        }
        return inserts;
    }

    // ===== 内部类 =====

    public static class TableDefinition {
        private String tableName;
        private List<TableColumnDefinition> columns;
        private String originalSql;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<TableColumnDefinition> getColumns() { return columns; }
        public void setColumns(List<TableColumnDefinition> columns) { this.columns = columns; }
        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    }

    public static class TableColumnDefinition {
        private String columnName;
        private String columnType;
        private boolean nullable;
        private boolean primaryKey;
        private boolean autoIncrement;

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getColumnType() { return columnType; }
        public void setColumnType(String columnType) { this.columnType = columnType; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
        public boolean isAutoIncrement() { return autoIncrement; }
        public void setAutoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; }
    }

    public static class InsertStatement {
        private String tableName;
        private String columns;
        private String values;
        private String originalSql;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumns() { return columns; }
        public void setColumns(String columns) { this.columns = columns; }
        public String getValues() { return values; }
        public void setValues(String values) { this.values = values; }
        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    }
}
