package io.github.openground.common.dbcheck.checker;

import io.github.openground.common.dbcheck.util.DbCheckUtils;
import io.github.openground.common.dbcheck.extractor.MetadataExtractor;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(DataSource.class)
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

        // 确定列名列表：INSERT 有显式列名则使用，否则从数据库元数据获取
        List<String> cols = insertStatements.get(0).getColumns();
        if (cols.isEmpty()) {
            cols = dbTableInfo.getColumns().stream()
                    .map(MetadataExtractor.DbColumnInfo::getName)
                    .collect(Collectors.toList());
        }
        String keyCol = cols.get(0);

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
            // 列名：优先使用 INSERT 中的显式列名，否则使用数据库元数据列名
            List<String> rowCols = stmt.getColumns().isEmpty() ? cols : stmt.getColumns();

            if (dbRow == null) {
                // DB 中不存在该行 → 整行缺失
                DataRecordDiff diff = new DataRecordDiff();
                diff.setTableName(tableName);
                diff.setMissing(true);
                for (int i = 0; i < Math.min(rowCols.size(), vals.size()); i++) {
                    diff.addField(rowCols.get(i), vals.get(i), null);
                }
                result.addDiff(tableName, diff);
                mismatchCount++;
            } else {
                // 存在则逐字段对比
                List<FieldDiff> fieldDiffs = new ArrayList<>();
                for (int i = 0; i < Math.min(rowCols.size(), vals.size()); i++) {
                    String col = rowCols.get(i);
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
        // 先反义 MySQL 转义（如 \' → '），再比较
        String sv = unescapeMysqlValue(scriptVal.trim());
        String dv = dbVal.trim();
        return sv.equals(dv);
    }

    /**
     * 按 MySQL 规则反义 SQL 字符串值中的反斜杠转义
     * <p>MySQL 默认将反斜杠视为转义字符，INSERT 脚本中的值经过 MySQL 存储后
     * 转义序列会被处理。对比时需要将脚本值做同样的反义处理。
     */
    private String unescapeMysqlValue(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '\\': sb.append('\\'); i++; break;
                    case '\'': sb.append('\''); i++; break;
                    case '"':  sb.append('"');  i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  sb.append('\r'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    case '0':  sb.append('\0'); i++; break;
                    case 'b':  sb.append('\b'); i++; break;
                    case 'Z':  sb.append((char) 26); i++; break;
                    default:
                        // MySQL 对无法识别的转义序列直接忽略反斜杠，保留后续字符
                        sb.append(next);
                        i++;
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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
        for (int i = 0; i < values.length(); i++) {
            char c = values.charAt(i);
            if (c == '\\' && i + 1 < values.length()) {
                // 保留反斜杠转义序列，由 valueEquals 中的 unescapeMysqlValue 统一处理
                current.append(c);
                current.append(values.charAt(i + 1));
                i++;
                continue;
            }
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
