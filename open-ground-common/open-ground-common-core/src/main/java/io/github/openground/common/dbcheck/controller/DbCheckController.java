package io.github.openground.common.dbcheck.controller;

import com.github.pagehelper.PageInfo;
import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.dto.PaginatedResult;
import io.github.openground.common.dbcheck.extractor.SqlScriptScanner;
import io.github.openground.common.dbcheck.model.CheckProgress;
import io.github.openground.common.dbcheck.model.DbCheckLogDO;
import io.github.openground.common.dbcheck.model.DbCheckProperties;
import io.github.openground.common.dbcheck.model.DbCheckResult;
import io.github.openground.common.dbcheck.model.ScriptInfo;
import io.github.openground.common.dbcheck.service.AsyncCheckService;
import io.github.openground.common.dbcheck.service.DbCheckLogService;
import io.github.openground.common.dbcheck.service.DbCheckService;
import io.github.openground.common.dbcheck.spi.DbCheckDatasourceProvider;
import io.github.openground.common.security.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DbCheck 数据库检查控制器
 *
 * <p>提供数据库表结构/数据/注释检查的手动触发接口。
 * 不依赖启动自动执行，所有操作由用户主动调用。
 *
 * @author ground-auth
 * @since 2026-06-15
 */
@Slf4j
@Tag(name = "数据库检查")
@RestController
@RequestMapping("/db-check")
@SuppressWarnings("all")
public class DbCheckController {

    @Autowired
    private DbCheckService dbCheckService;

    @Autowired
    private DbCheckProperties dbCheckProperties;

    @Autowired(required = false)
    private List<DbCheckDatasourceProvider> datasourceProviders;

    @Autowired
    private AsyncCheckService asyncCheckService;

    @Autowired
    private DbCheckLogService dbCheckLogService;

    

    /**
     * 执行数据库检查并同步
     *
     * <p>检查差异后生成并执行同步 SQL。
     * 注意：同步操作会修改数据库表结构和数据，请谨慎使用。
     *
     * @param params 包含 apply 字段（true=执行同步，false=仅生成 SQL）
     * @return 检查结果 + 执行的 SQL
     */
    @Operation(summary = "执行数据库检查并同步")
    @PostMapping("/sync")
    public CommonResult runSync(@RequestBody Map<String, Object> params) {
        boolean apply = Boolean.TRUE.equals(params.get("apply"));
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        log.info("手动触发 DbCheck 同步，apply={}, datasourceId={}", apply, datasourceId);

        long start = System.currentTimeMillis();
        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            DbCheckResult result = dbCheckService.runCheckWithSync(apply);
            saveCheckLog("sync", result, apply, null, System.currentTimeMillis() - start, getCurrentUserDisplay());
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage(apply ? "检查并同步完成" : "检查完成（未执行同步）")
                    .setData(result);
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            try {
                String dbType = provider.getDbType(datasourceId);
                if (dbType == null) continue;

                try (Connection conn = provider.getConnection(datasourceId)) {
                    DbCheckResult result = dbCheckService.runCheckWithConnection(conn, dbType);
                    if (apply && result.getSyncSqls() != null && !result.getSyncSqls().isEmpty()) {
                        dbCheckService.executeSqlsWithConn(conn, result.getSyncSqls());
                        result.setExecuted(true);
                    }
                    saveCheckLog("sync", result, apply, null, System.currentTimeMillis() - start, getCurrentUserDisplay());
                    return new CommonResult()
                            .setCode(ErrorCode.SUCCESS)
                            .setMessage(apply ? "检查并同步完成" : "检查完成")
                            .setData(result);
                }
            } catch (Exception e) {
                log.error("外部数据源同步失败 dsId={}: {}", datasourceId, e.getMessage());
                saveCheckLog("sync", new DbCheckResult(), apply, e.getMessage(), System.currentTimeMillis() - start, getCurrentUserDisplay());
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("同步失败: " + e.getMessage());
            }
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 获取 DbCheck 配置状态
     *
     * @return 配置信息
     */
    @Operation(summary = "获取 DbCheck 配置状态")
    @GetMapping("/status")
    public CommonResult getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", dbCheckProperties.isEnabled());
        status.put("mode", dbCheckProperties.getMode());
        status.put("dataCheck", dbCheckProperties.isDataCheck());
        status.put("commentCheck", dbCheckProperties.isCommentCheck());
        status.put("autoCheckOnStartup", dbCheckProperties.isAutoCheckOnStartup());
        status.put("databaseType", dbCheckService.resolveDbType());
        status.put("locations", dbCheckProperties.getLocations());
        status.put("hasExternalDatasources", datasourceProviders != null && !datasourceProviders.isEmpty());
        return new CommonResult()
                .setCode(ErrorCode.SUCCESS)
                .setMessage(ErrorCode.SUCCESS_MSG)
                .setData(status);
    }

    /**
     * 获取可用数据源列表（内置 + 外部 Provider 注册的数据源）
     *
     * @return 数据源列表，id=0 表示内置数据源
     */
    @Operation(summary = "获取可用数据源列表")
    @GetMapping("/datasources")
    public CommonResult listDatasources() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 1. 内置数据源（始终可用）
        Map<String, Object> builtin = new LinkedHashMap<>();
        builtin.put("id", 0L);
        builtin.put("name", "内置数据源");
        builtin.put("dbType", dbCheckService.resolveDbType());
        builtin.put("source", "builtin");
        list.add(builtin);

        // 2. 外部 Provider 数据源
        if (datasourceProviders != null) {
            for (DbCheckDatasourceProvider provider : datasourceProviders) {
                try {
                    for (DbCheckDatasourceProvider.DatasourceInfo info : provider.listDatasources()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", info.getId());
                        item.put("name", info.getName());
                        item.put("dbType", info.getDbType());
                        item.put("source", info.getSource());
                        list.add(item);
                    }
                } catch (Exception e) {
                    log.warn("获取外部数据源列表失败: {}", e.getMessage());
                }
            }
        }

        return new CommonResult()
                .setCode(ErrorCode.SUCCESS)
                .setMessage(ErrorCode.SUCCESS_MSG)
                .setData(list);
    }

    

    /**
     * 为勾选的表生成同步 SQL
     *
     * @param params 含 datasourceId（0=内置）和 tableNames（表名列表）
     * @return 同步 SQL 列表
     */
    @Operation(summary = "为勾选的表生成同步 SQL")
    @PostMapping("/generate-sql")
    public CommonResult generateSql(@RequestBody Map<String, Object> params) {
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<String> tableNames = (List<String>) params.get("tableNames");

        if (tableNames == null || tableNames.isEmpty()) {
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("未选择任何表")
                    .setData(Collections.emptyList());
        }

        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            String dbType = dbCheckService.resolveDbType();
            List<String> sqls = dbCheckService.generateSqlsForSelected(dbType, tableNames);
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("已生成 " + sqls.size() + " 条同步 SQL")
                    .setData(sqls);
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            try {
                String dbType = provider.getDbType(datasourceId);
                if (dbType == null) continue;

                try (Connection conn = provider.getConnection(datasourceId)) {
                    List<String> sqls = dbCheckService.generateSqlsForSelectedWithConn(conn, dbType, tableNames);
                    return new CommonResult()
                            .setCode(ErrorCode.SUCCESS)
                            .setMessage("已生成 " + sqls.size() + " 条同步 SQL")
                            .setData(sqls);
                }
            } catch (Exception e) {
                log.error("外部数据源生成SQL失败 dsId={}: {}", datasourceId, e.getMessage());
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("生成SQL失败: " + e.getMessage());
            }
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 为勾选的表生成完整 CREATE TABLE SQL
     *
     * <p>根据数据库实际表结构反向生成完整建表语句（与增量同步 SQL 不同，
     * 此接口从数据库元数据生成，包含所有字段/约束/注释的完整定义）。
     *
     * @param params 含 datasourceId（0=内置）和 tableNames（表名列表）
     * @return 完整 CREATE TABLE SQL 列表
     */
    @Operation(summary = "为勾选的表生成完整建表 SQL")
    @PostMapping("/generate-full-sql")
    public CommonResult generateFullSql(@RequestBody Map<String, Object> params) {
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<String> tableNames = (List<String>) params.get("tableNames");

        if (tableNames == null || tableNames.isEmpty()) {
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("未选择任何表")
                    .setData(Collections.emptyList());
        }

        long start = System.currentTimeMillis();
        String createBy = getCurrentUserDisplay();

        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            String dbType = dbCheckService.resolveDbType();
            List<String> sqls = dbCheckService.generateFullCreateSqls(dbType, tableNames);
            saveCheckLog("sync", makeLogResult(sqls.size()), false, null, System.currentTimeMillis() - start, createBy);
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("已生成 " + sqls.size() + " 条完整建表 SQL")
                    .setData(sqls);
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            try {
                String dbType = provider.getDbType(datasourceId);
                if (dbType == null) continue;

                try (Connection conn = provider.getConnection(datasourceId)) {
                    List<String> sqls = dbCheckService.generateFullCreateSqlsWithConn(conn, dbType, tableNames);
                    saveCheckLog("sync", makeLogResult(sqls.size()), false, null, System.currentTimeMillis() - start, createBy);
                    return new CommonResult()
                            .setCode(ErrorCode.SUCCESS)
                            .setMessage("已生成 " + sqls.size() + " 条完整建表 SQL")
                            .setData(sqls);
                }
            } catch (Exception e) {
                log.error("外部数据源生成完整SQL失败 dsId={}: {}", datasourceId, e.getMessage());
                saveCheckLog("sync", makeLogResult(0), false, e.getMessage(), System.currentTimeMillis() - start, createBy);
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("生成SQL失败: " + e.getMessage());
            }
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 为勾选的表生成增量 SQL 并立即执行到数据库
     *
     * <p>将 generate-sql 和 execute-sql 合并为一个操作，
     * 避免两次请求导致的一致性问题。
     *
     * @param params 含 datasourceId（0=内置）和 tableNames（表名列表）
     * @return 已执行的 SQL 条数
     */
    @Operation(summary = "为勾选的表生成增量 SQL 并立即执行到数据库")
    @PostMapping("/sync-tables")
    public CommonResult syncTables(@RequestBody Map<String, Object> params) {
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<String> tableNames = (List<String>) params.get("tableNames");

        if (tableNames == null || tableNames.isEmpty()) {
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("未选择任何表");
        }

        long start = System.currentTimeMillis();
        String createBy = getCurrentUserDisplay();

        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            try {
                String dbType = dbCheckService.resolveDbType();
                int sqlCount = dbCheckService.syncSelectedTables(dbType, tableNames);
                saveCheckLog("sync", makeLogResult(sqlCount), true, null, System.currentTimeMillis() - start, createBy);
                return new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage("成功同步 " + tableNames.size() + " 个表，执行 " + sqlCount + " 条 SQL")
                        .setData(Collections.singletonMap("sqlCount", sqlCount));
            } catch (Exception e) {
                log.error("同步表结构失败: {}", e.getMessage(), e);
                saveCheckLog("sync", makeLogResult(0), true, e.getMessage(), System.currentTimeMillis() - start, createBy);
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("同步失败: " + e.getMessage());
            }
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            try {
                String dbType = provider.getDbType(datasourceId);
                if (dbType == null) continue;

                try (Connection conn = provider.getConnection(datasourceId)) {
                    int sqlCount = dbCheckService.syncSelectedTablesWithConn(conn, dbType, tableNames);
                    saveCheckLog("sync", makeLogResult(sqlCount), true, null, System.currentTimeMillis() - start, createBy);
                    return new CommonResult()
                            .setCode(ErrorCode.SUCCESS)
                            .setMessage("成功同步 " + tableNames.size() + " 个表，执行 " + sqlCount + " 条 SQL");
                }
            } catch (Exception e) {
                log.error("外部数据源同步表结构失败 dsId={}: {}", datasourceId, e.getMessage());
                saveCheckLog("sync", makeLogResult(0), true, e.getMessage(), System.currentTimeMillis() - start, createBy);
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("同步失败: " + e.getMessage());
            }
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 执行给定的 SQL 语句
     *
     * @param params 含 datasourceId（0=内置）和 sqls（SQL 语句列表）
     * @return 执行结果
     */
    @Operation(summary = "执行同步 SQL")
    @PostMapping("/execute-sql")
    public CommonResult executeSql(@RequestBody Map<String, Object> params) {
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<String> sqls = (List<String>) params.get("sqls");

        if (sqls == null || sqls.isEmpty()) {
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("无 SQL 需要执行");
        }

        log.info("执行同步 SQL: dsId={}, sqlCount={}", datasourceId, sqls.size());
        long start = System.currentTimeMillis();

        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            try {
                dbCheckService.executeSqls(sqls);
                saveCheckLog("sync", makeLogResult(sqls.size()), true, null, System.currentTimeMillis() - start, getCurrentUserDisplay());
                return new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage("成功执行 " + sqls.size() + " 条 SQL");
            } catch (Exception e) {
                log.error("内置数据源执行SQL失败: {}", e.getMessage(), e);
                saveCheckLog("sync", makeLogResult(sqls.size()), true, e.getMessage(), System.currentTimeMillis() - start, getCurrentUserDisplay());
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("执行失败: " + e.getMessage());
            }
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            try {
                String dbType = provider.getDbType(datasourceId);
                if (dbType == null) continue;

                provider.executeSqls(datasourceId, sqls);
                saveCheckLog("sync", makeLogResult(sqls.size()), true, null, System.currentTimeMillis() - start, getCurrentUserDisplay());
                return new CommonResult()
                        .setCode(ErrorCode.SUCCESS)
                        .setMessage("成功执行 " + sqls.size() + " 条 SQL");
            } catch (Exception e) {
                log.error("外部数据源执行SQL失败 dsId={}: {}", datasourceId, e.getMessage());
                saveCheckLog("sync", makeLogResult(sqls.size()), true, e.getMessage(), System.currentTimeMillis() - start, getCurrentUserDisplay());
                return new CommonResult()
                        .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                        .setMessage("执行失败: " + e.getMessage());
            }
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 获取可用脚本文件列表
     *
     * <p>从全局 locations 扫描出所有 SQL 脚本，返回元信息供前端勾选。
     *
     * @return 脚本文件元信息列表
     */
    @Operation(summary = "获取可用脚本文件列表")
    @GetMapping("/scripts")
    public CommonResult listScripts() {
        List<ScriptInfo> scripts = dbCheckService.getScriptList();
        return new CommonResult()
                .setCode(ErrorCode.SUCCESS)
                .setMessage(ErrorCode.SUCCESS_MSG)
                .setData(scripts);
    }

    /**
     * 下载脚本文件
     *
     * <p>只允许下载当前脚本扫描结果中存在的文件，避免直接使用前端路径访问文件系统。
     *
     * @param scriptKey 脚本唯一标识
     * @return 脚本文件流
     */
    @Operation(summary = "下载脚本文件")
    @GetMapping("/scripts/download")
    public ResponseEntity<?> downloadScript(@RequestParam("scriptKey") String scriptKey) {
        SqlScriptScanner.SqlScript script = dbCheckService.getScriptByKey(scriptKey);
        if (script == null || script.getResource() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = script.getResource();
        String fileName = buildDownloadFileName(script);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(script.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    /**
     * 启动异步数据库检查
     *
     * <p>支持内置数据源（datasourceId=0/null）和外部数据源（datasourceId>0）。
     * 支持 scriptKeys 参数指定要检查的脚本文件（不传=全部脚本）。
     *
     * @param params 含 datasourceId（可选，默认0=内置）、tableNames（可选）、scriptKeys（可选）
     * @return 任务 ID
     */
    @Operation(summary = "异步执行数据库检查（支持内外数据源、指定脚本）")
    @PostMapping("/async/check")
    public CommonResult asyncCheck(@RequestBody Map<String, Object> params) {
        Long datasourceId = params.get("datasourceId") != null
                ? Long.valueOf(params.get("datasourceId").toString()) : 0L;
        @SuppressWarnings("unchecked")
        List<String> tableNames = (List<String>) params.get("tableNames");
        @SuppressWarnings("unchecked")
        List<String> scriptKeys = (List<String>) params.get("scriptKeys");

        // 内置数据源
        if (datasourceId == null || datasourceId == 0) {
            String dbType = dbCheckService.resolveDbType();
            String taskId = asyncCheckService.startCheck(dbType, tableNames, scriptKeys, getCurrentUserDisplay());
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("任务已提交")
                    .setData(Collections.singletonMap("taskId", taskId));
        }

        // 外部数据源
        if (datasourceProviders == null || datasourceProviders.isEmpty()) {
            return new CommonResult()
                    .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                    .setMessage("没有可用的外部数据源提供者");
        }

        for (DbCheckDatasourceProvider provider : datasourceProviders) {
            String dbType = provider.getDbType(datasourceId);
            if (dbType == null) continue;

            log.info("异步检查外部数据源: dsId={}, dbType={}, scriptKeys={}", datasourceId, dbType, scriptKeys != null ? scriptKeys.size() + "个" : "全部");
            String taskId = asyncCheckService.startCheckWithProvider(provider, datasourceId, dbType, tableNames, scriptKeys, getCurrentUserDisplay());
            return new CommonResult()
                    .setCode(ErrorCode.SUCCESS)
                    .setMessage("任务已提交")
                    .setData(Collections.singletonMap("taskId", taskId));
        }

        return new CommonResult()
                .setCode(String.valueOf(ErrorCode.DATABASE_EXCEPTION))
                .setMessage("未找到指定的数据源: " + datasourceId);
    }

    /**
     * 查询异步检查进度
     *
     * @param taskId 任务 ID
     * @return 当前进度
     */
    @Operation(summary = "查询异步检查进度")
    @GetMapping("/async/progress/{taskId}")
    public CommonResult getAsyncProgress(@PathVariable String taskId) {
        CheckProgress progress = asyncCheckService.getProgress(taskId);
        if (progress == null) {
            return new CommonResult().error(ErrorCode.DATABASE_EXCEPTION, "任务不存在");
        }
        return new CommonResult().success(progress);
    }

    /**
     * 分页查询操作日志
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 日志列表
     */
    @Operation(summary = "分页查询操作日志")
    @GetMapping("/log")
    public PaginatedResult listLogs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        DbCheckLogDO query = new DbCheckLogDO();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        PageInfo<DbCheckLogDO> page = dbCheckLogService.pageList(query);
        return PaginatedResult.success(page.getList(), page.getPageNum(), page.getTotal(), pageSize);
    }

    /**
     * 构造最小检查结果对象，用于 execute-sql 等场景的日志记录
     */
    private DbCheckResult makeLogResult(int sqlCount) {
        DbCheckResult result = new DbCheckResult();
        result.setScriptCount(0);
        List<String> syncSqls = new ArrayList<>(sqlCount);
        for (int i = 0; i < sqlCount; i++) {
            syncSqls.add("");
        }
        result.setSyncSqls(syncSqls);
        return result;
    }

    /**
     * 获取当前用户显示名（username/姓名）
     *
     * <p>取不到时返回 "system" 作为兜底。
     */
    private String getCurrentUserDisplay() {
        try {
            return SecurityContextHolder.getCurrentDisplayName();
        } catch (Exception e) {
            log.warn("获取当前用户失败，使用 system 兜底");
        }
        return "system";
    }
	private String buildDownloadFileName(SqlScriptScanner.SqlScript script) {
        String modulePath = script.getModulePath();
        String fileName = script.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "script.sql";
        }
        String downloadName = fileName;
        if (modulePath != null && !modulePath.trim().isEmpty()) {
            downloadName = modulePath + "_" + fileName;
        }
        return downloadName.replace("/", "_")
                .replace("\\", "_")
                .replace("\r", "")
                .replace("\n", "");
    }

    /**
     * 保存操作日志
     *
     * @param createBy 操作人（username/姓名格式）
     */
    private void saveCheckLog(String operationType, DbCheckResult result, boolean executed,
                              String errorMessage, long costMs, String createBy) {
        try {
            DbCheckLogDO log = new DbCheckLogDO();
            log.setOperationType(operationType);
            log.setDbType(result.getDbType());
            log.setScriptCount(result.getScriptCount());
            log.setMismatchCount(result.getDataResult() != null ? result.getDataResult().getConflictCount() : 0);
            log.setExtraRowCount(result.getDataResult() != null ? result.getDataResult().getUpsertCount() : 0);
            log.setSqlCount(result.getSyncSqls() != null ? result.getSyncSqls().size() : 0);
            log.setExecuted(executed ? "1" : "0");
            log.setSuccess(errorMessage == null ? "1" : "0");
            log.setErrorMessage(errorMessage);
            log.setCostMs(costMs);
            log.setCreateBy(createBy);
            dbCheckLogService.save(log);
        } catch (Exception e) {
            log.warn("保存 DbCheck 日志失败: {}", e.getMessage());
        }
    }
}
