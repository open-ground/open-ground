package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库表结构比较器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class DbSchemaComparator {

    /**
     * 比较 SQL 脚本中的表定义与实际数据库表结构
     *
     * @param scriptTables 脚本中解析出的表定义
     * @param dbTables     数据库中提取的表信息
     * @return 差异列表
     */
    public List<SchemaDiff> compare(List<SimpleSqlParser.TableDefinition> scriptTables,
                                    Map<String, MetadataExtractor.DbTableInfo> dbTables) {
        List<SchemaDiff> diffs = new ArrayList<>();
        Map<String, SimpleSqlParser.TableDefinition> scriptMap = new HashMap<>();
        for (SimpleSqlParser.TableDefinition def : scriptTables) {
            scriptMap.put(def.getTableName(), def);
        }

        // 比对脚本中的每个表
        for (SimpleSqlParser.TableDefinition scriptTable : scriptTables) {
            String tableName = scriptTable.getTableName();
            MetadataExtractor.DbTableInfo dbTable = dbTables.get(tableName);

            if (dbTable == null) {
                // 脚本中有但数据库中不存在
                SchemaDiff diff = new SchemaDiff();
                diff.setTableName(tableName);
                diff.setType("ONLY_IN_SCRIPT");
                diff.setDescription("表在脚本中存在但数据库中不存在");
                diffs.add(diff);
            } else {
                // 两者都有，比较列
                compareColumns(tableName, scriptTable.getColumns(), dbTable.getColumns(), diffs);
            }
        }

        // 数据库中有的但脚本中没有
        for (Map.Entry<String, MetadataExtractor.DbTableInfo> entry : dbTables.entrySet()) {
            if (!scriptMap.containsKey(entry.getKey())) {
                SchemaDiff diff = new SchemaDiff();
                diff.setTableName(entry.getKey());
                diff.setType("ONLY_IN_DATABASE");
                diff.setDescription("表在数据库中存在但脚本中不存在");
                diffs.add(diff);
            }
        }

        return diffs;
    }

    /**
     * 比较列差异
     */
    private void compareColumns(String tableName,
                                List<SimpleSqlParser.TableColumnDefinition> scriptCols,
                                List<MetadataExtractor.DbColumnInfo> dbCols,
                                List<SchemaDiff> diffs) {
        Map<String, MetadataExtractor.DbColumnInfo> dbColMap = new HashMap<>();
        for (MetadataExtractor.DbColumnInfo col : dbCols) {
            dbColMap.put(col.getColumnName(), col);
        }

        for (SimpleSqlParser.TableColumnDefinition scriptCol : scriptCols) {
            String colName = scriptCol.getColumnName();
            MetadataExtractor.DbColumnInfo dbCol = dbColMap.get(colName);

            if (dbCol == null) {
                // 列在数据库中缺失
                SchemaDiff diff = new SchemaDiff();
                diff.setTableName(tableName);
                diff.setColumnName(colName);
                diff.setType("MISSING_COLUMN");
                diff.setDescription("列 " + colName + " 在脚本中存在但数据库中不存在");
                diffs.add(diff);
            } else {
                // 比较列类型
                compareColumnType(tableName, scriptCol, dbCol, diffs);
            }
        }

        for (MetadataExtractor.DbColumnInfo dbCol : dbCols) {
            String colName = dbCol.getColumnName();
            boolean found = false;
            for (SimpleSqlParser.TableColumnDefinition scriptCol : scriptCols) {
                if (scriptCol.getColumnName().equals(colName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                SchemaDiff diff = new SchemaDiff();
                diff.setTableName(tableName);
                diff.setColumnName(colName);
                diff.setType("EXTRA_COLUMN");
                diff.setDescription("列 " + colName + " 在数据库中存在但脚本中不存在");
                diffs.add(diff);
            }
        }
    }

    /**
     * 比较列类型
     */
    private void compareColumnType(String tableName,
                                   SimpleSqlParser.TableColumnDefinition scriptCol,
                                   MetadataExtractor.DbColumnInfo dbCol,
                                   List<SchemaDiff> diffs) {
        // 标准化类型名进行比较
        String scriptType = normalizeType(scriptCol.getColumnType());
        String dbType = normalizeType(dbCol.getTypeName());

        if (!scriptType.equals(dbType)) {
            SchemaDiff diff = new SchemaDiff();
            diff.setTableName(tableName);
            diff.setColumnName(scriptCol.getColumnName());
            diff.setType("COLUMN_TYPE_MISMATCH");
            diff.setScriptType(scriptCol.getColumnType());
            diff.setDbType(dbCol.getTypeName());
            diff.setDescription("列 " + scriptCol.getColumnName()
                    + " 类型不一致: 脚本=" + scriptCol.getColumnType()
                    + ", 数据库=" + dbCol.getTypeName());
            diffs.add(diff);
        }
    }

    /**
     * 标准化类型名（去精度、转大写）
     */
    private String normalizeType(String type) {
        if (type == null) return "";
        String normalized = type.trim().toUpperCase();
        // 移除括号内的精度信息
        int parenIndex = normalized.indexOf('(');
        if (parenIndex > 0) {
            normalized = normalized.substring(0, parenIndex);
        }
        return normalized.trim();
    }

    // ===== 内部类 =====

    public static class SchemaDiff {
        private String tableName;
        private String columnName;
        private String type;
        private String scriptType;
        private String dbType;
        private String description;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getScriptType() { return scriptType; }
        public void setScriptType(String scriptType) { this.scriptType = scriptType; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class TableColumnDiff {
        private String columnName;
        private String scriptType;
        private String dbType;
        private boolean nullableDiff;
        private boolean defaultValueDiff;

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getScriptType() { return scriptType; }
        public void setScriptType(String scriptType) { this.scriptType = scriptType; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public boolean isNullableDiff() { return nullableDiff; }
        public void setNullableDiff(boolean nullableDiff) { this.nullableDiff = nullableDiff; }
        public boolean isDefaultValueDiff() { return defaultValueDiff; }
        public void setDefaultValueDiff(boolean defaultValueDiff) { this.defaultValueDiff = defaultValueDiff; }
    }

    public static class ColumnTypeMismatch {
        private String tableName;
        private String columnName;
        private String scriptType;
        private String dbType;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getScriptType() { return scriptType; }
        public void setScriptType(String scriptType) { this.scriptType = scriptType; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
    }

    public static class NullableDiff {
        private String tableName;
        private String columnName;
        private boolean scriptNullable;
        private boolean dbNullable;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public boolean isScriptNullable() { return scriptNullable; }
        public void setScriptNullable(boolean scriptNullable) { this.scriptNullable = scriptNullable; }
        public boolean isDbNullable() { return dbNullable; }
        public void setDbNullable(boolean dbNullable) { this.dbNullable = dbNullable; }
    }
}
