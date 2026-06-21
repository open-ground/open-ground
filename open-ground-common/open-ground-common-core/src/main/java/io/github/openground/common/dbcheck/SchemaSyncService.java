package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * 表结构同步服务
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class SchemaSyncService {

    /**
     * 执行表结构同步 SQL
     *
     * @param conn JDBC 连接
     * @param sqls 同步 SQL 列表（ALTER TABLE / CREATE TABLE 等）
     * @return 同步结果
     */
    public DbCheckResult.SyncResult syncSchema(Connection conn, List<String> sqls) {
        DbCheckResult.SyncResult result = new DbCheckResult.SyncResult();
        result.setTotal(sqls.size());
        int success = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                try {
                    stmt.execute(sql);
                    success++;
                    log.debug("同步 SQL 执行成功: {}", sql.substring(0, Math.min(sql.length(), 80)));
                } catch (Exception e) {
                    failed++;
                    String errMsg = "SQL 执行失败: " + e.getMessage()
                            + " | SQL: " + sql.substring(0, Math.min(sql.length(), 100));
                    errors.add(errMsg);
                    log.warn("同步 SQL 执行失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("表结构同步过程中发生异常", e);
            errors.add("同步异常: " + e.getMessage());
        }

        result.setSuccess(success);
        result.setFailed(failed);
        result.setErrors(errors);
        return result;
    }
}
