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

    /** 同步 SQL 列表 */
    private List<String> syncSqls;

    /** 同步结果（执行同步后的反馈） */
    private SyncResult syncResult;

    public DbCheckResult() {
        this.syncSqls = new ArrayList<>();
    }

    // ===== Getters & Setters =====

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

    public SyncResult getSyncResult() { return syncResult; }
    public void setSyncResult(SyncResult syncResult) { this.syncResult = syncResult; }

    // ===== 内部结果类 =====

    /**
     * 表结构差异结果
     */
    public static class SchemaCheckResult {
        private int totalTables;
        private int matchedTables;
        private int mismatchedTables;
        private int onlyInScript;
        private int onlyInDatabase;
        private List<TableDiff> diffs;

        public SchemaCheckResult() { this.diffs = new ArrayList<>(); }

        public int getTotalTables() { return totalTables; }
        public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
        public int getMatchedTables() { return matchedTables; }
        public void setMatchedTables(int matchedTables) { this.matchedTables = matchedTables; }
        public int getMismatchedTables() { return mismatchedTables; }
        public void setMismatchedTables(int mismatchedTables) { this.mismatchedTables = mismatchedTables; }
        public int getOnlyInScript() { return onlyInScript; }
        public void setOnlyInScript(int onlyInScript) { this.onlyInScript = onlyInScript; }
        public int getOnlyInDatabase() { return onlyInDatabase; }
        public void setOnlyInDatabase(int onlyInDatabase) { this.onlyInDatabase = onlyInDatabase; }
        public List<TableDiff> getDiffs() { return diffs; }
        public void setDiffs(List<TableDiff> diffs) { this.diffs = diffs; }
    }

    /**
     * 单表差异
     */
    public static class TableDiff {
        private String tableName;
        private String status;      // MISMATCH, ONLY_IN_SCRIPT, ONLY_IN_DB
        private List<String> details;

        public TableDiff() { this.details = new ArrayList<>(); }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getDetails() { return details; }
        public void setDetails(List<String> details) { this.details = details; }
    }

    /**
     * 数据差异结果
     */
    public static class DataCheckResult {
        private int totalTables;
        private int conflictCount;
        private int upsertCount;
        private List<DataDiff> diffs;

        public DataCheckResult() { this.diffs = new ArrayList<>(); }

        public int getTotalTables() { return totalTables; }
        public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
        public int getConflictCount() { return conflictCount; }
        public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }
        public int getUpsertCount() { return upsertCount; }
        public void setUpsertCount(int upsertCount) { this.upsertCount = upsertCount; }
        public List<DataDiff> getDiffs() { return diffs; }
        public void setDiffs(List<DataDiff> diffs) { this.diffs = diffs; }
    }

    /**
     * 单表数据差异
     */
    public static class DataDiff {
        private String tableName;
        private int conflictRows;
        private int upsertRows;
        private List<String> sampleConflicts;

        public DataDiff() { this.sampleConflicts = new ArrayList<>(); }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public int getConflictRows() { return conflictRows; }
        public void setConflictRows(int conflictRows) { this.conflictRows = conflictRows; }
        public int getUpsertRows() { return upsertRows; }
        public void setUpsertRows(int upsertRows) { this.upsertRows = upsertRows; }
        public List<String> getSampleConflicts() { return sampleConflicts; }
        public void setSampleConflicts(List<String> sampleConflicts) { this.sampleConflicts = sampleConflicts; }
    }

    /**
     * 注释差异结果
     */
    public static class CommentCheckResult {
        private int totalTables;
        private int mismatchCount;
        private List<CommentDiff> diffs;

        public CommentCheckResult() { this.diffs = new ArrayList<>(); }

        public int getTotalTables() { return totalTables; }
        public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
        public int getMismatchCount() { return mismatchCount; }
        public void setMismatchCount(int mismatchCount) { this.mismatchCount = mismatchCount; }
        public List<CommentDiff> getDiffs() { return diffs; }
        public void setDiffs(List<CommentDiff> diffs) { this.diffs = diffs; }
    }

    /**
     * 单表注释差异
     */
    public static class CommentDiff {
        private String tableName;
        private String fieldName;
        private String expected;
        private String actual;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getExpected() { return expected; }
        public void setExpected(String expected) { this.expected = expected; }
        public String getActual() { return actual; }
        public void setActual(String actual) { this.actual = actual; }
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        private int total;
        private int success;
        private int failed;
        private List<String> errors;

        public SyncResult() { this.errors = new ArrayList<>(); }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    // ===== 兼容性方法 =====

    /** @deprecated 仅用于旧版 JSON 序列化兼容 */
    @Deprecated
    public Map<String, Object> toLegacyMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("dbType", dbType);
        map.put("scriptCount", scriptCount);
        map.put("syncSqls", syncSqls);
        if (schemaResult != null) {
            Map<String, Object> sr = new HashMap<>();
            sr.put("totalTables", schemaResult.totalTables);
            sr.put("matchedTables", schemaResult.matchedTables);
            sr.put("mismatchedTables", schemaResult.mismatchedTables);
            sr.put("onlyInScript", schemaResult.onlyInScript);
            sr.put("onlyInDatabase", schemaResult.onlyInDatabase);
            map.put("schemaResult", sr);
        }
        return map;
    }
}
