package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@Service
public class AsyncCheckService {

    private final DbCheckService dbCheckService;
    private final DbCheckLogService dbCheckLogService;
    private final Map<String, CheckProgress> progressMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AsyncCheckService(DbCheckService dbCheckService, DbCheckLogService dbCheckLogService) {
        this.dbCheckService = dbCheckService;
        this.dbCheckLogService = dbCheckLogService;
    }

    /**
     * 启动异步检查（内置数据源）
     *
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名（为空则全量检查）
     * @param createBy   操作人（username/姓名格式）
     * @return 任务 ID
     */
    public String startCheck(String dbType, List<String> tableNames, String createBy) {
        return startCheck(dbType, tableNames, null, createBy);
    }

    /**
     * 启动异步检查（内置数据源，支持指定脚本）
     *
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名（为空则全量检查）
     * @param scriptKeys 要检查的脚本唯一标识列表（null=全部）
     * @param createBy   操作人
     * @return 任务 ID
     */
    public String startCheck(String dbType, List<String> tableNames, List<String> scriptKeys, String createBy) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        CheckProgress progress = new CheckProgress(taskId);
        progressMap.put(taskId, progress);

        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                progress.update(5, "扫描脚本", "正在扫描 SQL 脚本文件...");
                if (tableNames != null && !tableNames.isEmpty()) {
                    // 仅生成勾选表的同步 SQL
                    List<String> sqls = dbCheckService.generateSqlsForSelected(dbType, tableNames);
                    DbCheckResult result = new DbCheckResult();
                    result.setSyncSqls(sqls);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                    progress.finish(result);
                } else {
                    // 全量检查（带脚本过滤）
                    DbCheckResult result = runCheckWithProgress(
                            () -> dbCheckService.runCheck(scriptKeys), progress);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                }
            } catch (Exception e) {
                log.error("异步检查失败 taskId={}", taskId, e);
                saveAsyncLog(taskId, new DbCheckResult(), false, e.getMessage(), System.currentTimeMillis() - start, createBy);
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
     * @param createBy   操作人（username/姓名格式）
     * @return 任务 ID
     */
    public String startCheckWithConn(Connection conn, String dbType, List<String> tableNames, String createBy) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        CheckProgress progress = new CheckProgress(taskId);
        progressMap.put(taskId, progress);

        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                progress.update(5, "扫描脚本", "正在扫描 SQL 脚本文件...");
                if (tableNames != null && !tableNames.isEmpty()) {
                    List<String> sqls = dbCheckService.generateSqlsForSelectedWithConn(conn, dbType, tableNames);
                    DbCheckResult result = new DbCheckResult();
                    result.setSyncSqls(sqls);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                    progress.finish(result);
                } else {
                    DbCheckResult result = runCheckWithProgress(() -> dbCheckService.runCheckWithConnection(conn, dbType), progress);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                }
            } catch (Exception e) {
                log.error("异步检查失败 taskId={}", taskId, e);
                saveAsyncLog(taskId, new DbCheckResult(), false, e.getMessage(), System.currentTimeMillis() - start, createBy);
                progress.fail(e.getMessage());
            }
        });

        return taskId;
    }

    /**
     * 启动异步检查（外部数据源 — 通过 Provider 自动管理连接）
     *
     * <p>与 {@link #startCheckWithConn} 的区别：此方法在异步线程内部
     * 通过 Provider 获取连接、执行检查、自动关闭连接，调用方无需管理连接生命周期。
     *
     * @param provider   数据源提供者
     * @param datasourceId 数据源 ID
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名（为空则全量检查）
     * @param createBy   操作人（username/姓名格式）
     * @return 任务 ID
     */
    public String startCheckWithProvider(DbCheckDatasourceProvider provider, Long datasourceId,
                                          String dbType, List<String> tableNames, String createBy) {
        return startCheckWithProvider(provider, datasourceId, dbType, tableNames, null, createBy);
    }

    /**
     * 启动异步检查（外部数据源 — 通过 Provider 自动管理连接，支持指定脚本）
     *
     * @param provider    数据源提供者
     * @param datasourceId 数据源 ID
     * @param dbType      数据库类型
     * @param tableNames  勾选的表名（为空则全量检查）
     * @param scriptKeys  要检查的脚本唯一标识列表（null=全部）
     * @param createBy    操作人
     * @return 任务 ID
     */
    public String startCheckWithProvider(DbCheckDatasourceProvider provider, Long datasourceId,
                                          String dbType, List<String> tableNames,
                                          List<String> scriptKeys, String createBy) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        CheckProgress progress = new CheckProgress(taskId);
        progressMap.put(taskId, progress);

        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try (Connection conn = provider.getConnection(datasourceId)) {
                progress.update(5, "扫描脚本", "正在扫描 SQL 脚本文件...");
                if (tableNames != null && !tableNames.isEmpty()) {
                    List<String> sqls = dbCheckService.generateSqlsForSelectedWithConn(conn, dbType, tableNames);
                    DbCheckResult result = new DbCheckResult();
                    result.setDbType(dbType);
                    result.setSyncSqls(sqls);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                    progress.finish(result);
                } else {
                    // 全量检查（带脚本过滤）
                    DbCheckResult result = runCheckWithProgress(
                            () -> dbCheckService.runCheckWithConnection(conn, dbType, scriptKeys), progress);
                    result.setDbType(dbType);
                    saveAsyncLog(taskId, result, false, null, System.currentTimeMillis() - start, createBy);
                }
            } catch (Exception e) {
                log.error("异步检查失败 taskId={}, datasourceId={}", taskId, datasourceId, e);
                DbCheckResult errResult = new DbCheckResult();
                errResult.setDbType(dbType);
                saveAsyncLog(taskId, errResult, false, e.getMessage(), System.currentTimeMillis() - start, createBy);
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
     * 保存异步检查日志
     */
    private void saveAsyncLog(String taskId, DbCheckResult result, boolean executed,
                               String errorMessage, long costMs, String createBy) {
        try {
            DbCheckLogDO logDo = new DbCheckLogDO();
            logDo.setId(UUID.randomUUID().toString().replace("-", ""));
            logDo.setOperationType("check");
            logDo.setDbType(result.getDbType());
            logDo.setScriptCount(result.getScriptCount());
            logDo.setMismatchCount(result.getDataResult() != null ? result.getDataResult().getConflictCount() : 0);
            logDo.setExtraRowCount(result.getDataResult() != null ? result.getDataResult().getUpsertCount() : 0);
            logDo.setSqlCount(result.getSyncSqls() != null ? result.getSyncSqls().size() : 0);
            logDo.setExecuted(executed ? "1" : "0");
            logDo.setSuccess(errorMessage == null ? "1" : "0");
            logDo.setErrorMessage(errorMessage);
            logDo.setCostMs(costMs);
            logDo.setCreateBy(createBy);
            logDo.setCreateTime(new Date());
            logDo.setUpdateTime(new Date());
            dbCheckLogService.save(logDo);
            log.debug("异步检查日志已保存: taskId={}, type={}, success={}", taskId, "check", logDo.getSuccess());
        } catch (Exception e) {
            log.warn("保存异步检查日志失败 taskId={}: {}", taskId, e.getMessage());
        }
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

    @FunctionalInterface
    private interface Callable<V> {
        V call() throws Exception;
    }
}
