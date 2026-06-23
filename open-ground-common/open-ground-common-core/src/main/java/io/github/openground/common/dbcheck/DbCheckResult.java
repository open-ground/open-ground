package io.github.openground.common.dbcheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DbCheck 检查结果 DTO
 *
 * <p>包含表结构差异、数据差异、注释差异和同步 SQL 的完整检查报告。
 *
 * @author open-ground
 * @since 2026-06-18
 */
public class DbCheckResult {

    /** 数据库类型 */
    private String dbType;

    /** 脚本数量 */
    private int scriptCount;

    /** 表结构差异 */
    private SchemaCheckResult schemaResult;

    /** 数据差异 */
    private DataCheckResult dataResult;

    /** 注释差异 */
    private CommentCheckResult commentResult;

    /** 生成的同步 SQL 列表 */
    private List<String> syncSqls;

    /** 是否已执行同步 */
    private boolean executed;

    /** 执行耗时（毫秒） */
    private long elapsedMs;

    /** SQL 解析错误列表 */
    private List<ParseErrorInfo> parseErrors;

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public int getScriptCount() { return scriptCount; }
    public void setScriptCount(int scriptCount) { this.scriptCount = scriptCount; }

    public SchemaCheckResult getSchemaResult() { return schemaResult; }
    public void setSchemaResult(SchemaCheckResult schemaResult) { this.schemaResult = schemaResult; }

    public DataCheckResult getDataResult() { return dataResult; }
    public void setDataResult(DataCheckResult dataResult) { this.dataResult = dataResult; }

    public CommentCheckResult getCommentResult() { return commentResult; }
    public void setCommentResult(CommentCheckResult commentResult) { this.commentResult = commentResult; }

    public List<String> getSyncSqls() { return syncSqls; }
    public void setSyncSqls(List<String> syncSqls) { this.syncSqls = syncSqls; }

    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public List<ParseErrorInfo> getParseErrors() { return parseErrors; }
    public void setParseErrors(List<ParseErrorInfo> parseErrors) { this.parseErrors = parseErrors; }

    // ===== 内部类 =====

    public static class SchemaCheckResult {
        private boolean consistent = true;
        private List<String> missingTables = new ArrayList<>();
        private List<String> extraTables = new ArrayList<>();
        private List<ColumnDiffItem> columnDiffs = new ArrayList<>();
        private Map<String, List<String>> pkDiffs = new HashMap<>();
        private Map<String, List<String>> indexDiffs = new HashMap<>();

        public boolean isConsistent() { return consistent; }
        public void setConsistent(boolean consistent) { this.consistent = consistent; }
        public List<String> getMissingTables() { return missingTables; }
        public void setMissingTables(List<String> missingTables) { this.missingTables = missingTables; }
        public List<String> getExtraTables() { return extraTables; }
        public void setExtraTables(List<String> extraTables) { this.extraTables = extraTables; }
        public List<ColumnDiffItem> getColumnDiffs() { return columnDiffs; }
        public void setColumnDiffs(List<ColumnDiffItem> columnDiffs) { this.columnDiffs = columnDiffs; }
        public Map<String, List<String>> getPkDiffs() { return pkDiffs; }
        public void setPkDiffs(Map<String, List<String>> pkDiffs) { this.pkDiffs = pkDiffs; }
        public Map<String, List<String>> getIndexDiffs() { return indexDiffs; }
        public void setIndexDiffs(Map<String, List<String>> indexDiffs) { this.indexDiffs = indexDiffs; }
    }

    public static class ColumnDiffItem {
        private String tableName;
        private List<String> missingColumns = new ArrayList<>();
        private List<String> extraColumns = new ArrayList<>();
        private List<String> typeMismatches = new ArrayList<>();
        private List<String> nullableDiffs = new ArrayList<>();

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getMissingColumns() { return missingColumns; }
        public void setMissingColumns(List<String> missingColumns) { this.missingColumns = missingColumns; }
        public List<String> getExtraColumns() { return extraColumns; }
        public void setExtraColumns(List<String> extraColumns) { this.extraColumns = extraColumns; }
        public List<String> getTypeMismatches() { return typeMismatches; }
        public void setTypeMismatches(List<String> typeMismatches) { this.typeMismatches = typeMismatches; }
        public List<String> getNullableDiffs() { return nullableDiffs; }
        public void setNullableDiffs(List<String> nullableDiffs) { this.nullableDiffs = nullableDiffs; }
    }

    public static class DataCheckResult {
        private boolean consistent = true;
        private int conflictCount;
        private int upsertCount;
        private List<String> diffs = new ArrayList<>();
        private List<DataDiffItem> diffDetails = new ArrayList<>();

        public boolean isConsistent() { return consistent; }
        public void setConsistent(boolean consistent) { this.consistent = consistent; }
        public int getConflictCount() { return conflictCount; }
        public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }
        public int getUpsertCount() { return upsertCount; }
        public void setUpsertCount(int upsertCount) { this.upsertCount = upsertCount; }
        public List<String> getDiffs() { return diffs; }
        public void setDiffs(List<String> diffs) { this.diffs = diffs; }
        public List<DataDiffItem> getDiffDetails() { return diffDetails; }
        public void setDiffDetails(List<DataDiffItem> diffDetails) { this.diffDetails = diffDetails; }
    }

    public static class DataDiffItem {
        private String table;
        private boolean missing;
        private List<Map<String, String>> fields = new ArrayList<>();

        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public boolean isMissing() { return missing; }
        public void setMissing(boolean missing) { this.missing = missing; }
        public List<Map<String, String>> getFields() { return fields; }
        public void setFields(List<Map<String, String>> fields) { this.fields = fields; }
    }

    public static class CommentCheckResult {
        private boolean consistent = true;
        private List<Map<String, String>> mismatches = new ArrayList<>();
        private List<Map<String, String>> missingComments = new ArrayList<>();

        public boolean isConsistent() { return consistent; }
        public void setConsistent(boolean consistent) { this.consistent = consistent; }
        public List<Map<String, String>> getMismatches() { return mismatches; }
        public void setMismatches(List<Map<String, String>> mismatches) { this.mismatches = mismatches; }
        public List<Map<String, String>> getMissingComments() { return missingComments; }
        public void setMissingComments(List<Map<String, String>> missingComments) { this.missingComments = missingComments; }
    }

    /**
     * SQL 解析错误信息
     */
    public static class ParseErrorInfo {
        private String sqlPreview;
        private String errorMessage;

        public String getSqlPreview() { return sqlPreview; }
        public void setSqlPreview(String sqlPreview) { this.sqlPreview = sqlPreview; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
