package io.github.openground.common.dbcheck.checker;

import io.github.openground.common.dbcheck.util.DbCheckUtils;
import io.github.openground.common.dbcheck.extractor.MetadataExtractor;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库表结构比较器
 * 对比脚本表结构与数据库表结构的差异
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class DbSchemaComparator {

    /**
     * 对比表结构差异（使用元数据方式）
     *
     * @param scriptTables 脚本中的表结构（从简单SQL解析）
     * @param dbTables 数据库中的表结构（从元数据提取）
     * @return 表结构差异报告
     */
    public SchemaDiff compareTables(List<SimpleSqlParser.TableDefinition> scriptTables,
                                   List<MetadataExtractor.DbTableInfo> dbTables) {
        SchemaDiff diff = new SchemaDiff();

        // 创建表名到表信息的映射
        Map<String, SimpleSqlParser.TableDefinition> scriptTableMap = new HashMap<>();
        Map<String, MetadataExtractor.DbTableInfo> dbTableMap = new HashMap<>();

        for (SimpleSqlParser.TableDefinition table : scriptTables) {
            scriptTableMap.put(table.getTableName().toLowerCase(), table);
        }

        for (MetadataExtractor.DbTableInfo table : dbTables) {
            dbTableMap.put(table.getTableName().toLowerCase(), table);
        }

        // 检查脚本中的表是否在数据库中存在
        for (Map.Entry<String, SimpleSqlParser.TableDefinition> entry : scriptTableMap.entrySet()) {
            String tableName = entry.getKey();
            SimpleSqlParser.TableDefinition scriptTable = entry.getValue();

            if (!dbTableMap.containsKey(tableName)) {
                // 表不存在，需要创建
                diff.addMissingTable(scriptTable);
            } else {
                // 表存在，检查字段差异
                MetadataExtractor.DbTableInfo dbTable = dbTableMap.get(tableName);
                TableColumnDiff columnDiff = compareColumns(scriptTable, dbTable);
                if (!columnDiff.isEmpty()) {
                    diff.addColumnDiff(tableName, columnDiff);
                }
                // 检查主键差异
                PkDiff pkDiff = comparePrimaryKeys(scriptTable, dbTable);
                if (pkDiff != null) {
                    diff.addPkDiff(tableName, pkDiff);
                }
                // 检查索引差异
                List<IndexDiff> indexDiffs = compareIndexes(scriptTable, dbTable);
                if (!indexDiffs.isEmpty()) {
                    diff.addIndexDiffs(tableName, indexDiffs);
                }
            }
        }

        // 检查数据库中多余的表
        for (Map.Entry<String, MetadataExtractor.DbTableInfo> entry : dbTableMap.entrySet()) {
            String tableName = entry.getKey();
            if (!scriptTableMap.containsKey(tableName)) {
                diff.addExtraTable(entry.getValue());
            }
        }

        return diff;
    }

    /**
     * 对比表字段差异
     */
    private TableColumnDiff compareColumns(SimpleSqlParser.TableDefinition scriptTable, 
                                          MetadataExtractor.DbTableInfo dbTable) {
        TableColumnDiff diff = new TableColumnDiff();

        // 创建字段名到字段信息的映射
        Map<String, SimpleSqlParser.TableColumnDefinition> scriptColumnMap = new HashMap<>();
        Map<String, MetadataExtractor.DbColumnInfo> dbColumnMap = new HashMap<>();

        for (SimpleSqlParser.TableColumnDefinition column : scriptTable.getColumns()) {
            scriptColumnMap.put(column.getName().toLowerCase(), column);
        }

        for (MetadataExtractor.DbColumnInfo column : dbTable.getColumns()) {
            dbColumnMap.put(column.getName().toLowerCase(), column);
        }

        // 检查脚本中的字段是否在数据库中存在
        for (Map.Entry<String, SimpleSqlParser.TableColumnDefinition> entry : scriptColumnMap.entrySet()) {
            String columnName = entry.getKey();
            SimpleSqlParser.TableColumnDefinition scriptColumn = entry.getValue();

            if (!dbColumnMap.containsKey(columnName)) {
                // 字段不存在，需要添加
                diff.addMissingColumn(scriptColumn);
            } else {
                // 字段存在，检查类型是否一致
                MetadataExtractor.DbColumnInfo dbColumn = dbColumnMap.get(columnName);
                if (!isColumnTypeMatch(scriptColumn, dbColumn)) {
                    diff.addTypeMismatch(scriptColumn, dbColumn);
                } else {
                    // 类型一致，再检查非空约束
                    boolean scriptNotNull = isScriptNotNull(scriptColumn.getAttributes());
                    boolean dbNotNull = "NO".equalsIgnoreCase(dbColumn.getNullable());
                    if (scriptNotNull != dbNotNull) {
                        diff.addNullableDiff(scriptColumn, dbColumn);
                    }
                }
            }
        }

        // 检查数据库中多余的字段
        for (Map.Entry<String, MetadataExtractor.DbColumnInfo> entry : dbColumnMap.entrySet()) {
            String columnName = entry.getKey();
            if (!scriptColumnMap.containsKey(columnName)) {
                diff.addExtraColumn(entry.getValue());
            }
        }

        return diff;
    }

    /**
     * 对比主键差异
     */
    private PkDiff comparePrimaryKeys(SimpleSqlParser.TableDefinition scriptTable,
                                       MetadataExtractor.DbTableInfo dbTable) {
        List<String> scriptPk = scriptTable.getPrimaryKeyColumns();
        List<String> dbPk = dbTable.getPrimaryKeyColumns();

        // 两者都为空 → 一致
        if ((scriptPk == null || scriptPk.isEmpty()) && (dbPk == null || dbPk.isEmpty())) {
            return null;
        }
        // 一个为空一个非空 → 有差异
        if (scriptPk == null || scriptPk.isEmpty()) {
            PkDiff d = new PkDiff();
            d.setScriptPk(new ArrayList<>());
            d.setDbPk(new ArrayList<>(dbPk));
            d.setDbPkName(dbTable.getPrimaryKeyName());
            return d;
        }
        if (dbPk == null || dbPk.isEmpty()) {
            PkDiff d = new PkDiff();
            d.setScriptPk(new ArrayList<>(scriptPk));
            d.setDbPk(new ArrayList<>());
            return d;
        }
        // 比较列表内容是否完全一致（顺序也要一致）
        if (scriptPk.size() != dbPk.size()) {
            PkDiff d = new PkDiff();
            d.setScriptPk(new ArrayList<>(scriptPk));
            d.setDbPk(new ArrayList<>(dbPk));
            d.setDbPkName(dbTable.getPrimaryKeyName());
            return d;
        }
        for (int i = 0; i < scriptPk.size(); i++) {
            if (!scriptPk.get(i).equalsIgnoreCase(dbPk.get(i))) {
                PkDiff d = new PkDiff();
                d.setScriptPk(new ArrayList<>(scriptPk));
                d.setDbPk(new ArrayList<>(dbPk));
                d.setDbPkName(dbTable.getPrimaryKeyName());
                return d;
            }
        }
        return null;
    }

    /**
     * 对比索引差异（以脚本为准，脚本有而数据库没有的索引为缺失索引）
     */
    private List<IndexDiff> compareIndexes(SimpleSqlParser.TableDefinition scriptTable,
                                            MetadataExtractor.DbTableInfo dbTable) {
        List<IndexDiff> result = new ArrayList<>();

        List<SimpleSqlParser.ScriptIndexInfo> scriptIndexes = scriptTable.getIndexList();
        List<MetadataExtractor.DbIndexInfo> dbIndexes = dbTable.getIndexList();

        // 构建数据库索引映射：indexName(小写) + 列列表 → 索引
        Map<String, MetadataExtractor.DbIndexInfo> dbIndexMap = new HashMap<>();
        if (dbIndexes != null) {
            for (MetadataExtractor.DbIndexInfo idx : dbIndexes) {
                String key = buildIndexKey(idx.getIndexName(), idx.getColumnNames());
                if (key != null) {
                    dbIndexMap.put(key, idx);
                }
            }
        }

        // 以脚本为准，检查脚本索引在数据库中是否存在
        if (scriptIndexes != null) {
            for (SimpleSqlParser.ScriptIndexInfo scriptIdx : scriptIndexes) {
                String scriptKey = buildIndexKey(scriptIdx.getIndexName(), scriptIdx.getColumnNames());
                if (scriptKey == null) continue;

                MetadataExtractor.DbIndexInfo matched = dbIndexMap.get(scriptKey);
                if (matched == null) {
                    // 尝试按列名+唯一性匹配（索引名不同但定义相同）
                    matched = findDbIndexByColumns(scriptIdx, dbIndexes);
                }

                if (matched == null) {
                    IndexDiff d = new IndexDiff();
                    d.setType("missing");
                    d.setScriptIndexName(scriptIdx.getIndexName());
                    d.setScriptColumns(new ArrayList<>(scriptIdx.getColumnNames()));
                    d.setScriptUnique(scriptIdx.isUnique());
                    MetadataExtractor.DbIndexInfo sameName = findDbIndexByName(scriptIdx.getIndexName(), dbIndexes);
                    if (sameName != null) {
                        d.setDbIndexName(sameName.getIndexName());
                        d.setDbUnique(sameName.isUnique());
                    }
                    result.add(d);
                } else {
                    // 索引存在，检查唯一性是否一致
                    if (scriptIdx.isUnique() != matched.isUnique()) {
                        IndexDiff d = new IndexDiff();
                        d.setType("unique_mismatch");
                        d.setScriptIndexName(scriptIdx.getIndexName());
                        d.setScriptColumns(new ArrayList<>(scriptIdx.getColumnNames()));
                        d.setScriptUnique(scriptIdx.isUnique());
                        d.setDbIndexName(matched.getIndexName());
                        d.setDbUnique(matched.isUnique());
                        result.add(d);
                    }
                }
            }
        }

        return result;
    }

    private String buildIndexKey(String indexName, List<String> columns) {
        if (columns == null || columns.isEmpty()) return null;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) key.append(",");
            key.append(columns.get(i).toLowerCase());
        }
        // 如果索引名不为空，也加入 key 以区分同名不同定义的情况
        if (indexName != null && !indexName.isEmpty()) {
            return indexName.toLowerCase() + "|" + key.toString();
        }
        return key.toString();
    }

    private MetadataExtractor.DbIndexInfo findDbIndexByColumns(SimpleSqlParser.ScriptIndexInfo scriptIdx,
                                                                List<MetadataExtractor.DbIndexInfo> dbIndexes) {
        if (dbIndexes == null || dbIndexes.isEmpty()) return null;
        for (MetadataExtractor.DbIndexInfo dbIdx : dbIndexes) {
            if (dbIdx.getColumnNames() == null || dbIdx.getColumnNames().isEmpty()) continue;
            if (dbIdx.getColumnNames().size() != scriptIdx.getColumnNames().size()) continue;
            boolean match = true;
            for (int i = 0; i < dbIdx.getColumnNames().size(); i++) {
                if (!dbIdx.getColumnNames().get(i).equalsIgnoreCase(scriptIdx.getColumnNames().get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return dbIdx;
            }
        }
        return null;
    }

    private MetadataExtractor.DbIndexInfo findDbIndexByName(String indexName,
                                                            List<MetadataExtractor.DbIndexInfo> dbIndexes) {
        if (indexName == null || indexName.isEmpty() || dbIndexes == null || dbIndexes.isEmpty()) {
            return null;
        }
        for (MetadataExtractor.DbIndexInfo dbIdx : dbIndexes) {
            if (dbIdx.getIndexName() != null && indexName.equalsIgnoreCase(dbIdx.getIndexName())) {
                return dbIdx;
            }
        }
        return null;
    }

    /**
     * 检查字段类型是否匹配
     */
    private boolean isColumnTypeMatch(SimpleSqlParser.TableColumnDefinition scriptColumn, 
                                     MetadataExtractor.DbColumnInfo dbColumn) {
        String scriptType = normalizeType(scriptColumn.getType());
        String dbType = normalizeType(dbColumn.getType());
        if (scriptType.equals(dbType)) {
            return isLengthMatch(scriptType, scriptColumn.getLength(), dbColumn.getSize());
        }
        // MySQL JDBC 驱动对 TINYINT(1) 列会返回 TYPE_NAME=BIT
        if ("TINYINT".equals(scriptType) && "BIT".equals(dbType) && dbColumn.getSize() == 1) {
            return true;
        }
        if ("BIT".equals(scriptType) && "TINYINT".equals(dbType) && scriptColumn.getLength() != null
                && "1".equals(scriptColumn.getLength().trim())) {
            return true;
        }
        return false;
    }

    /**
     * 从字段属性中提取是否 NOT NULL
     */
    private boolean isScriptNotNull(String attributes) {
        if (attributes == null) return false;
        String upper = attributes.toUpperCase();
        // NOT NULL 可以出现在不同位置：有的驱动报告为 "NOT NULL"，有的为 "NOT_NULL"
        if (upper.contains("NOT NULL") || upper.contains("NOT_NULL")) return true;
        // PRIMARY KEY 隐含 NOT NULL
        if (upper.contains("PRIMARY KEY")) return true;
        return false;
    }

    /**
     * 检查长度是否匹配（仅对 VARCHAR/CHAR/DECIMAL/NUMERIC 等有长度语义的类型）
     */
    private boolean isLengthMatch(String normalizedType, String scriptLength, int dbSize) {
        // 脚本未指定长度，不做长度校验
        if (scriptLength == null || scriptLength.trim().isEmpty()) {
            return true;
        }
        switch (normalizedType) {
            case "VARCHAR":
            case "CHAR":
                // scriptLength 如 "255"，dbSize 是 JDBC COLUMN_SIZE
                return parseInt(scriptLength) == dbSize;
            case "DECIMAL":
            case "NUMERIC":
                // scriptLength 如 "10,2"（精度,小数位），dbSize 只报告精度
                String prec = scriptLength.split(",")[0].trim();
                return parseInt(prec) == dbSize;
            case "INT":
            case "BIGINT":
            case "SMALLINT":
            case "TINYINT":
                // 整数类型虽有长度但通常无业务含义，且不同驱动报告差异大，跳过
                return true;
            default:
                return true;
        }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    /**
     * 类型名称归一化，消除不同厂商/同义词导致的假差异
     */
    private String normalizeType(String raw) {
        return DbCheckUtils.normalizeType(raw);
    }

    // ========== 内部实体类 ==========

    public static class SchemaDiff {
        private List<SimpleSqlParser.TableDefinition> missingTables = new ArrayList<>();
        private List<MetadataExtractor.DbTableInfo> extraTables = new ArrayList<>();
        private Map<String, TableColumnDiff> columnDiffs = new HashMap<>();
        private Map<String, PkDiff> pkDiffs = new HashMap<>();
        private Map<String, List<IndexDiff>> indexDiffs = new HashMap<>();

        public void addMissingTable(SimpleSqlParser.TableDefinition table) {
            missingTables.add(table);
        }

        public void addExtraTable(MetadataExtractor.DbTableInfo table) {
            extraTables.add(table);
        }

        public void addColumnDiff(String tableName, TableColumnDiff diff) {
            columnDiffs.put(tableName, diff);
        }

        public void addPkDiff(String tableName, PkDiff diff) {
            pkDiffs.put(tableName, diff);
        }

        public void addIndexDiffs(String tableName, List<IndexDiff> diffs) {
            indexDiffs.put(tableName, diffs);
        }

        public boolean isEmpty() {
            return missingTables.isEmpty() && extraTables.isEmpty() && columnDiffs.isEmpty()
                    && pkDiffs.isEmpty() && indexDiffs.isEmpty();
        }

        // Getters
        public List<SimpleSqlParser.TableDefinition> getMissingTables() { return missingTables; }
        public List<MetadataExtractor.DbTableInfo> getExtraTables() { return extraTables; }
        public Map<String, TableColumnDiff> getColumnDiffs() { return columnDiffs; }
        public Map<String, PkDiff> getPkDiffs() { return pkDiffs; }
        public Map<String, List<IndexDiff>> getIndexDiffs() { return indexDiffs; }
    }

    public static class TableColumnDiff {
        private List<SimpleSqlParser.TableColumnDefinition> missingColumns = new ArrayList<>();
        private List<MetadataExtractor.DbColumnInfo> extraColumns = new ArrayList<>();
        private Map<String, ColumnTypeMismatch> typeMismatches = new HashMap<>();
        private List<NullableDiff> nullableDiffs = new ArrayList<>();

        public void addMissingColumn(SimpleSqlParser.TableColumnDefinition column) {
            missingColumns.add(column);
        }

        public void addExtraColumn(MetadataExtractor.DbColumnInfo column) {
            extraColumns.add(column);
        }

        public void addTypeMismatch(SimpleSqlParser.TableColumnDefinition scriptColumn, 
                                   MetadataExtractor.DbColumnInfo dbColumn) {
            typeMismatches.put(scriptColumn.getName(), 
                new ColumnTypeMismatch(scriptColumn, dbColumn));
        }

        public void addNullableDiff(SimpleSqlParser.TableColumnDefinition scriptColumn,
                                    MetadataExtractor.DbColumnInfo dbColumn) {
            boolean scriptNotNull = scriptColumn.getAttributes() != null
                    && scriptColumn.getAttributes().toUpperCase().contains("NOT NULL");
            boolean dbNotNull = "NO".equalsIgnoreCase(dbColumn.getNullable());
            nullableDiffs.add(new NullableDiff(
                    scriptColumn.getName(), scriptNotNull, dbNotNull,
                    scriptColumn.getType(), scriptColumn.getLength(), scriptColumn.getAttributes()));
        }

        public boolean isEmpty() {
            return missingColumns.isEmpty() && extraColumns.isEmpty()
                    && typeMismatches.isEmpty() && nullableDiffs.isEmpty();
        }

        public List<SimpleSqlParser.TableColumnDefinition> getMissingColumns() { return missingColumns; }
        public List<MetadataExtractor.DbColumnInfo> getExtraColumns() { return extraColumns; }
        public Map<String, ColumnTypeMismatch> getTypeMismatches() { return typeMismatches; }
        public List<NullableDiff> getNullableDiffs() { return nullableDiffs; }
    }

    public static class NullableDiff {
        private String columnName;
        private boolean scriptNotNull;
        private boolean dbNotNull;
        private String type;
        private String length;
        private String attributes;

        public NullableDiff(String columnName, boolean scriptNotNull, boolean dbNotNull,
                           String type, String length, String attributes) {
            this.columnName = columnName;
            this.scriptNotNull = scriptNotNull;
            this.dbNotNull = dbNotNull;
            this.type = type;
            this.length = length;
            this.attributes = attributes;
        }
        public String getColumnName() { return columnName; }
        public boolean isScriptNotNull() { return scriptNotNull; }
        public boolean isDbNotNull() { return dbNotNull; }
        public String getType() { return type; }
        public String getLength() { return length; }
        public String getAttributes() { return attributes; }
    }

    public static class ColumnTypeMismatch {
        private SimpleSqlParser.TableColumnDefinition scriptColumn;
        private MetadataExtractor.DbColumnInfo dbColumn;

        public ColumnTypeMismatch(SimpleSqlParser.TableColumnDefinition scriptColumn, 
                                 MetadataExtractor.DbColumnInfo dbColumn) {
            this.scriptColumn = scriptColumn;
            this.dbColumn = dbColumn;
        }

        // Getters
        public SimpleSqlParser.TableColumnDefinition getScriptColumn() { return scriptColumn; }
        public MetadataExtractor.DbColumnInfo getDbColumn() { return dbColumn; }
    }

    /**
     * 主键差异
     */
    public static class PkDiff {
        private List<String> scriptPk = new ArrayList<>();
        private List<String> dbPk = new ArrayList<>();
        private String dbPkName;

        public List<String> getScriptPk() { return scriptPk; }
        public void setScriptPk(List<String> scriptPk) { this.scriptPk = scriptPk; }
        public List<String> getDbPk() { return dbPk; }
        public void setDbPk(List<String> dbPk) { this.dbPk = dbPk; }
        public String getDbPkName() { return dbPkName; }
        public void setDbPkName(String dbPkName) { this.dbPkName = dbPkName; }
    }

    /**
     * 索引差异项
     */
    public static class IndexDiff {
        private String type;            // missing / unique_mismatch
        private String scriptIndexName;
        private List<String> scriptColumns = new ArrayList<>();
        private boolean scriptUnique;
        private String dbIndexName;
        private boolean dbUnique;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getScriptIndexName() { return scriptIndexName; }
        public void setScriptIndexName(String scriptIndexName) { this.scriptIndexName = scriptIndexName; }
        public List<String> getScriptColumns() { return scriptColumns; }
        public void setScriptColumns(List<String> scriptColumns) { this.scriptColumns = scriptColumns; }
        public boolean isScriptUnique() { return scriptUnique; }
        public void setScriptUnique(boolean scriptUnique) { this.scriptUnique = scriptUnique; }
        public String getDbIndexName() { return dbIndexName; }
        public void setDbIndexName(String dbIndexName) { this.dbIndexName = dbIndexName; }
        public boolean isDbUnique() { return dbUnique; }
        public void setDbUnique(boolean dbUnique) { this.dbUnique = dbUnique; }
    }
}
