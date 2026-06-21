package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * 数据同步服务
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class DataSyncService {

    /**
     * 同步数据（执行 INSERT 语句）
     *
     * @param conn  JDBC 连接
     * @param sqls  要执行的 SQL 列表
     * @return 同步结果
     */
    public DbCheckResult.SyncResult syncData(Connection conn, List<String> sqls) {
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
                } catch (Exception e) {
                    failed++;
                    errors.add("SQL 执行失败: " + e.getMessage() + " | SQL: " + sql.substring(0, Math.min(sql.length(), 100)));
                    log.warn("数据同步 SQL 执行失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("数据同步过程中发生异常", e);
            errors.add("数据同步异常: " + e.getMessage());
        }

        result.setSuccess(success);
        result.setFailed(failed);
        result.setErrors(errors);
        return result;
    }
}
