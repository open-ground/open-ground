package io.github.openground.common.dbcheck.service;

import io.github.openground.common.dbcheck.util.DbCheckUtils;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步服务
 * 执行数据同步，支持主键一致强制覆盖
 * <p>多数据库 UPSERT 策略：
 * <ul>
 *   <li>MySQL → {@code INSERT ... ON DUPLICATE KEY UPDATE}</li>
 *   <li>PostgreSQL → {@code INSERT ... ON CONFLICT (col) DO UPDATE SET}</li>
 *   <li>Oracle/DM → {@code DELETE + INSERT} 组合语句</li>
 * </ul>
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class DataSyncService {

    private final DataSource dataSource;

    public DataSyncService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行数据同步
     *
     * @param insertStatements INSERT 语句列表
     * @param dbType 数据库类型
     * @param deleteExtraData 是否删除多余数据
     * @return 同步结果
     */
    public DataSyncResult syncData(List<SimpleSqlParser.InsertStatement> insertStatements,
                                  String dbType,
                                  boolean deleteExtraData) {
        DataSyncResult result = new DataSyncResult();

        if (insertStatements.isEmpty()) {
            log.info("No INSERT statements to sync");
            return result;
        }

        // 生成同步 SQL
        List<String> syncSqls = generateDataSyncSqls(insertStatements, dbType, deleteExtraData);
        result.setSyncSqls(syncSqls);

        log.info("Generated {} SQL statements for data sync", syncSqls.size());
        return result;
    }

    /**
     * 生成数据同步 SQL 语句
     */
    private List<String> generateDataSyncSqls(List<SimpleSqlParser.InsertStatement> insertStatements,
                                             String dbType,
                                             boolean deleteExtraData) {
        List<String> sqls = new ArrayList<>();

        for (SimpleSqlParser.InsertStatement insertStmt : insertStatements) {
            List<String> stmtSqls = generateUpsertSql(insertStmt, dbType);
            sqls.addAll(stmtSqls);
        }

        // 如果需要删除多余数据，生成 DELETE 语句
        if (deleteExtraData) {
            log.info("Extra data deletion is enabled, but implementation requires database query");
        }

        return sqls;
    }

    /**
     * 生成 UPSERT SQL（多数据库兼容）
     * <p>返回 SQL 列表（Oracle/DM 返回 [DELETE, INSERT] 两条）
     */
    private List<String> generateUpsertSql(SimpleSqlParser.InsertStatement insertStmt, String dbType) {
        List<String> sqls = new ArrayList<>();

        String tableName = insertStmt.getTableName();
        List<String> columns = insertStmt.getColumns();
        String values = insertStmt.getValues();

        String quotedTable = DbCheckUtils.quoteId(tableName, dbType);

        // 构建 INSERT 部分（公共）
        StringBuilder insertSql = new StringBuilder();
        insertSql.append("INSERT INTO ").append(quotedTable).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            insertSql.append(DbCheckUtils.quoteId(columns.get(i), dbType));
            if (i < columns.size() - 1) {
                insertSql.append(", ");
            }
        }
        insertSql.append(") VALUES (").append(values).append(")");

        // MySQL: INSERT ... ON DUPLICATE KEY UPDATE
        if (DbCheckUtils.isMysql(dbType)) {
            insertSql.append(" AS new ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < columns.size(); i++) {
                insertSql.append(DbCheckUtils.quoteId(columns.get(i), dbType));
                // MySQL 8.0.20+ 推荐使用 NEW.col 替代 VALUES(col)
                insertSql.append(" = new.").append(DbCheckUtils.quoteId(columns.get(i), dbType));
                if (i < columns.size() - 1) {
                    insertSql.append(", ");
                }
            }
            sqls.add(insertSql.toString());
        }
        // PostgreSQL: INSERT ... ON CONFLICT (col1) DO UPDATE SET
        else if (DbCheckUtils.isPostgresql(dbType)) {
            String firstCol = DbCheckUtils.quoteId(columns.get(0), dbType);
            insertSql.append(" ON CONFLICT (").append(firstCol).append(") DO UPDATE SET ");
            for (int i = 0; i < columns.size(); i++) {
                insertSql.append(DbCheckUtils.quoteId(columns.get(i), dbType));
                insertSql.append(" = EXCLUDED.").append(DbCheckUtils.quoteId(columns.get(i), dbType));
                if (i < columns.size() - 1) {
                    insertSql.append(", ");
                }
            }
            sqls.add(insertSql.toString());
        }
        // Oracle/DM: DELETE + INSERT（先删再插，以第一个字段为键）
        else if (DbCheckUtils.isOracleOrDm(dbType)) {
            String firstCol = DbCheckUtils.quoteId(columns.get(0), dbType);
            // 从 values 中提取第一个字段的值
            List<String> valList = parseValues(values);
            String firstVal = valList.isEmpty() ? "" : valList.get(0);
            // DELETE 语句
            StringBuilder deleteSql = new StringBuilder();
            deleteSql.append("DELETE FROM ").append(quotedTable)
                    .append(" WHERE ").append(firstCol).append(" = '").append(firstVal).append("'");
            sqls.add(deleteSql.toString());
            sqls.add(insertSql.toString());
        }
        // 兜底：纯 INSERT
        else {
            sqls.add(insertSql.toString());
        }

        return sqls;
    }

    /**
     * 简单解析 VALUES 字符串（与 DataChecker.parseValues 保持一致）
     */
    private List<String> parseValues(String values) {
        if (values == null || values.trim().isEmpty()) return new ArrayList<>();
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

    /**
     * 实际执行同步 SQL 语句
     */
    public void executeSyncSqls(List<String> sqls) {
        if (sqls.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            for (String sql : sqls) {
                log.info("Executing data sync SQL: {}", sql);
                stmt.executeUpdate(sql);
            }
            conn.commit();
            log.info("Data sync executed successfully: {} SQLs", sqls.size());
        } catch (Exception e) {
            log.error("Data sync execution failed", e);
            throw new RuntimeException("Data sync failed", e);
        }
    }

    /**
     * 执行同步 SQL（使用外部连接）
     */
    public void executeSyncSqls(Connection conn, List<String> sqls) {
        if (sqls.isEmpty()) return;
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            for (String sql : sqls) {
                log.info("Executing data sync SQL: {}", sql);
                stmt.executeUpdate(sql);
            }
            conn.commit();
            log.info("Data sync executed successfully: {} SQLs", sqls.size());
        } catch (Exception e) {
            log.error("Data sync execution failed", e);
            throw new RuntimeException("Data sync failed", e);
        }
    }

    // ========== 内部实体类 ==========

    public static class DataSyncResult {
        private List<String> syncSqls = new ArrayList<>();
        private boolean success;
        private String errorMessage;

        // Getters and Setters
        public List<String> getSyncSqls() { return syncSqls; }
        public void setSyncSqls(List<String> syncSqls) { this.syncSqls = syncSqls; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
