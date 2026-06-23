package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据检查器
 * 检查 INSERT 脚本数据与数据库实际数据的一致性
 * <p>优化：按表批量查询，避免逐行 SELECT 的 N+1 问题。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class DataChecker {

    private final DataSource dataSource;

    public DataChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 检查脚本 INSERT 数据与数据库数据是否一致（内置数据源）
     */
    public DataCheckResult checkDataConsistency(List<SimpleSqlParser.InsertStatement> insertStatements,
                                                MetadataExtractor.DbTableInfo dbTableInfo,
                                                String dbType) {
        try (Connection conn = dataSource.getConnection()) {
            return checkDataConsistency(insertStatements, dbTableInfo, dbType, conn);
        } catch (Exception e) {
            log.error("Failed to get connection for data check", e);
            return new DataCheckResult();
        }
    }

    /**
     * 检查脚本 INSERT 数据与数据库数据是否一致（使用外部连接）
     * <p>批量查询：按表一次性查出所有匹配行，内存中对比，避免 N+1。
     */
    public DataCheckResult checkDataConsistency(List<SimpleSqlParser.InsertStatement> insertStatements,
                                                MetadataExtractor.DbTableInfo dbTableInfo,
                                                String dbType,
                                                Connection conn) {
        DataCheckResult result = new DataCheckResult();
        if (insertStatements.isEmpty() || dbTableInfo == null) return result;

        String tableName = dbTableInfo.getTableName();
        String keyCol = insertStatements.get(0).getColumns().get(0);

        // 提取所有键值和条目
        List<String> keys = new ArrayList<>();
        List<List<String>> allVals = new ArrayList<>();
        for (SimpleSqlParser.InsertStatement stmt : insertStatements) {
            List<String> vals = parseValues(stmt.getValues());
            allVals.add(vals);
            if (!vals.isEmpty()) {
                keys.add(vals.get(0));
            }
        }
        if (keys.isEmpty()) return result;

        // 批量查询：一次查出所有匹配行
        Map<String, Map<String, String>> dbRows = batchQuery(conn, tableName, keyCol, keys, dbType);

        // 逐行对比
        int mismatchCount = 0;
        for (int idx = 0; idx < insertStatements.size(); idx++) {
            SimpleSqlParser.InsertStatement stmt = insertStatements.get(idx);
            List<String> vals = allVals.get(idx);
            String keyVal = vals.isEmpty() ? "" : vals.get(0);
            Map<String, String> dbRow = dbRows.get(keyVal);
            List<String> cols = stmt.getColumns();

            if (dbRow == null) {
                // DB 中不存在该行 → 整行缺失
                DataRecordDiff diff = new DataRecordDiff();
                diff.setTableName(tableName);
                diff.setMissing(true);
                for (int i = 0; i < Math.min(cols.size(), vals.size()); i++) {
                    diff.addField(cols.get(i), vals.get(i), null);
                }
                result.addDiff(tableName, diff);
                mismatchCount++;
            } else {
                // 存在则逐字段对比
                List<FieldDiff> fieldDiffs = new ArrayList<>();
                for (int i = 0; i < Math.min(cols.size(), vals.size()); i++) {
                    String col = cols.get(i);
                    String scriptVal = vals.get(i);
                    String dbVal = dbRow.get(col.toLowerCase());
                    if (!valueEquals(scriptVal, dbVal)) {
                        fieldDiffs.add(new FieldDiff(col, scriptVal, dbVal != null ? dbVal : "NULL"));
                    }
                }
                if (!fieldDiffs.isEmpty()) {
                    DataRecordDiff diff = new DataRecordDiff();
                    diff.setTableName(tableName);
                    diff.setFields(fieldDiffs);
                    diff.setMissing(false);
                    result.addDiff(tableName, diff);
                    mismatchCount++;
                }
            }
        }

        // 统计 DB 中多余的行数（仅计数，不输出明细）
        int totalRows = countRows(tableName, dbType, conn);
        int scriptRows = insertStatements.size();
        if (totalRows > scriptRows) {
            result.setExtraRowCount(totalRows - scriptRows);
        }

        if (mismatchCount > 0 || result.getExtraRowCount() > 0) {
            result.setMismatchCount(mismatchCount);
        }

        return result;
    }

    /**
     * 批量查询：一次查出所有键值对应的行
     */
    private Map<String, Map<String, String>> batchQuery(Connection conn, String tableName,
                                                         String keyCol, List<String> keys,
                                                         String dbType) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (keys.isEmpty()) return result;

        String quotedTable = DbCheckUtils.quoteId(tableName, dbType);
        String quotedKey = DbCheckUtils.quoteId(keyCol, dbType);

        // 分批查询，避免 IN 子句过长（每批 500 条）
        int batchSize = 500;
        for (int start = 0; start < keys.size(); start += batchSize) {
            int end = Math.min(start + batchSize, keys.size());
            List<String> batchKeys = keys.subList(start, end);

            String placeholders = batchKeys.stream().map(k -> "?").collect(Collectors.joining(", "));
            String sql = "SELECT * FROM " + quotedTable + " WHERE " + quotedKey + " IN (" + placeholders + ")";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < batchKeys.size(); i++) {
                    ps.setString(i + 1, batchKeys.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                            String cn = rsmd.getColumnName(i);
                            String cv = rs.getString(i);
                            row.put(cn.toLowerCase(), cv != null ? cv.trim() : null);
                        }
                        // 用第一个字段（键）的值作为 map key
                        String rowKey = rs.getString(1);
                        if (rowKey != null) {
                            result.put(rowKey.trim(), row);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("批量查询失败: table={}, keyCol={}, sql={}", tableName, keyCol, sql, e);
            }
        }

        return result;
    }

    private boolean valueEquals(String scriptVal, String dbVal) {
        if (scriptVal == null && dbVal == null) return true;
        if (scriptVal == null || dbVal == null) return false;
        String sv = scriptVal.replace("'", "").trim();
        String dv = dbVal.trim();
        return sv.equals(dv);
    }

    private int countRows(String tableName, String dbType) {
        try (Connection conn = dataSource.getConnection()) {
            return countRows(tableName, dbType, conn);
        } catch (Exception e) {
            log.error("Count rows failed for table {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    private int countRows(String tableName, String dbType, Connection conn) {
        String sql = "SELECT COUNT(1) FROM " + DbCheckUtils.quoteId(tableName, dbType);
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            log.error("Count rows failed for table {}: {}", tableName, e.getMessage());
        }
        return 0;
    }

    private List<String> parseValues(String values) {
        if (values == null || values.trim().isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : values.toCharArray()) {
            if (c == '\'') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) {
                String val = current.toString().trim();
                result.add("NULL".equalsIgnoreCase(val) ? null : val);
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            String val = current.toString().trim();
            result.add("NULL".equalsIgnoreCase(val) ? null : val);
        }
        return result;
    }

    // ========== 内部实体类 ==========

    public static class DataCheckResult {
        private final List<DataRecordDiff> diffs = new ArrayList<>();
        private int mismatchCount;
        private int extraRowCount;

        public void addDiff(String tableName, DataRecordDiff diff) {
            diffs.add(diff);
        }
        public boolean isEmpty() { return diffs.isEmpty() && extraRowCount == 0; }
        public List<DataRecordDiff> getDiffs() { return diffs; }
        public int getMismatchCount() { return mismatchCount; }
        public void setMismatchCount(int mismatchCount) { this.mismatchCount = mismatchCount; }
        public int getExtraRowCount() { return extraRowCount; }
        public void setExtraRowCount(int extraRowCount) { this.extraRowCount = extraRowCount; }
    }

    public static class DataRecordDiff {
        private String tableName;
        private boolean missing;
        private List<FieldDiff> fields = new ArrayList<>();

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public boolean isMissing() { return missing; }
        public void setMissing(boolean missing) { this.missing = missing; }
        public List<FieldDiff> getFields() { return fields; }
        public void setFields(List<FieldDiff> fields) { this.fields = fields; }
        public void addField(String column, String scriptVal, String dbVal) {
            this.fields.add(new FieldDiff(column, scriptVal, dbVal));
        }
    }

    public static class FieldDiff {
        private String column;
        private String scriptVal;
        private String dbVal;

        public FieldDiff(String column, String scriptVal, String dbVal) {
            this.column = column;
            this.scriptVal = scriptVal;
            this.dbVal = dbVal;
        }
        public String getColumn() { return column; }
        public String getScriptVal() { return scriptVal != null ? scriptVal : "NULL"; }
        public String getDbVal() { return dbVal != null ? dbVal : "NULL"; }
    }
}
