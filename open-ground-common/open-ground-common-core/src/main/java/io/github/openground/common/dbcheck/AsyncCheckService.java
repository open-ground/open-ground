package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步检查服务
 * <p>在后台线程执行数据库检查，支持前端轮询进度。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class AsyncCheckService {

    private final DbCheckService dbCheckService;
    private final Map<String, CheckProgress> progressMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AsyncCheckService(DbCheckService dbCheckService) {
        this.dbCheckService = dbCheckService;
    }

    /**
     * 启动异步检查（内置数据源）
     *
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名（为空则全量检查）
     * @return 任务 ID
     */
    public String startCheck(String dbType, List<String> tableNames) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        CheckProgress progress = new CheckProgress(taskId);
        progressMap.put(taskId, progress);

        executor.submit(() -> {
            try {
                progress.update(5, "扫描脚本", "正在扫描 SQL 脚本文件...");
                if (tableNames != null && !tableNames.isEmpty()) {
                    // 仅生成勾选表的同步 SQL
                    List<String> sqls = dbCheckService.generateSqlsForSelected(dbType, tableNames);
                    DbCheckResult result = new DbCheckResult();
                    result.setSyncSqls(sqls);
                    progress.finish(result);
                } else {
                    // 全量检查
                    DbCheckResult result = runCheckWithProgress(() -> dbCheckService.runCheck(dbType), progress);
                }
            } catch (Exception e) {
                log.error("异步检查失败 taskId={}", taskId, e);
                progress.fail(e.getMessage());
            }
        });

        return taskId;
    }

    /**
     * 启动异步检查（外部数据源）
     *
     * @param conn       JDBC 连接
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名（为空则全量检查）
     * @return 任务 ID
     */
    public String startCheckWithConn(Connection conn, String dbType, List<String> tableNames) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        CheckProgress progress = new CheckProgress(taskId);
        progressMap.put(taskId, progress);

        executor.submit(() -> {
            try {
                progress.update(5, "扫描脚本", "正在扫描 SQL 脚本文件...");
                if (tableNames != null && !tableNames.isEmpty()) {
                    List<String> sqls = dbCheckService.generateSqlsForSelectedWithConn(conn, dbType, tableNames);
                    DbCheckResult result = new DbCheckResult();
                    result.setSyncSqls(sqls);
                    progress.finish(result);
                } else {
                    DbCheckResult result = runCheckWithProgress(
                            () -> dbCheckService.runCheckWithConnection(conn, dbType), progress);
                }
            } catch (Exception e) {
                log.error("异步检查失败 taskId={}", taskId, e);
                progress.fail(e.getMessage());
            }
        });

        return taskId;
    }

    /**
     * 执行全量检查并更新进度
     */
    private DbCheckResult runCheckWithProgress(Callable<DbCheckResult> callable, CheckProgress progress) throws Exception {
        progress.update(10, "扫描脚本", "正在扫描 SQL 脚本文件...");
        Thread.sleep(50);

        progress.update(20, "解析表结构", "正在解析 CREATE TABLE 语句...");
        Thread.sleep(50);

        progress.update(35, "获取元数据", "正在从数据库读取表结构信息...");
        Thread.sleep(50);

        progress.update(50, "比对差异", "正在比对脚本与数据库的差异...");
        Thread.sleep(50);

        DbCheckResult result = callable.call();

        progress.update(90, "生成 SQL", "正在生成同步 SQL...");
        Thread.sleep(50);

        progress.finish(result);
        return result;
    }

    /**
     * 获取任务进度
     */
    public CheckProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    /**
     * 移除已完成的任务（由前端或定时任务调用）
     */
    public void removeTask(String taskId) {
        progressMap.remove(taskId);
    }
}
