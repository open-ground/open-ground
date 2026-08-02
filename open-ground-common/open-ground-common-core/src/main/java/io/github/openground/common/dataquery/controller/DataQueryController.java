package io.github.openground.common.dataquery.controller;

import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.dataquery.service.DataQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.util.Map;

/**
 * 数据查询与导出控制器
 *
 * <p>提供指定数据源的表结构查询、自定义 SQL 查询、结果导出（Excel/CSV）接口。
 * 仅支持 SELECT 查询，禁止 DML/DDL 操作。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Tag(name = "数据查询导出")
@RestController
@RequestMapping("/data-query")
@SuppressWarnings("all")
public class DataQueryController {

    @Autowired
    private DataQueryService dataQueryService;

    /**
     * 获取所有可用数据源列表
     *
     * @return 数据源信息列表
     */
    @Operation(summary = "获取数据源列表")
    @GetMapping("/datasources")
    public CommonResult listDatasources() {
        return CommonResult.success(dataQueryService.listDataSources());
    }

    /**
     * 获取指定数据源的表列表
     *
     * @param dsName 数据源名称
     * @return 表信息列表
     */
    @Operation(summary = "获取表列表")
    @GetMapping("/tables")
    public CommonResult listTables(@RequestParam("dsName") String dsName) {
        try {
            return CommonResult.success(dataQueryService.listTables(dsName));
        } catch (IllegalArgumentException e) {
            return CommonResult.error(ErrorCode.PARAMETER_ILLEGAL_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("获取表列表失败: dsName={}, error={}", dsName, e);
            return CommonResult.error(ErrorCode.DATABASE_EXCEPTION, "数据源连接失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定表的列信息
     *
     * @param dsName    数据源名称
     * @param tableName 表名
     * @return 列信息列表
     */
    @Operation(summary = "获取表字段信息")
    @GetMapping("/columns")
    public CommonResult listColumns(@RequestParam("dsName") String dsName,
                                    @RequestParam("tableName") String tableName) {
        try {
            return CommonResult.success(dataQueryService.listTableColumns(dsName, tableName));
        } catch (IllegalArgumentException e) {
            return CommonResult.error(ErrorCode.PARAMETER_ILLEGAL_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("获取表字段失败: dsName={}, table={}, error={}", dsName, tableName, e.getMessage());
            return CommonResult.error(ErrorCode.DATABASE_EXCEPTION, "数据源连接失败: " + e.getMessage());
        }
    }

    /**
     * 执行自定义 SQL 查询
     *
     * <p>仅支持 SELECT 语句。支持分页查询。
     *
     * @param params 含 dsName（数据源名称）、sql（查询SQL）、pageIndex（页码，可选）、pageSize（每页条数，可选）
     * @return 查询结果（列名 + 数据行 + 总数）
     */
    @Operation(summary = "执行SQL查询")
    @PostMapping("/execute")
    public CommonResult executeQuery(@RequestBody Map<String, Object> params) {
        try {
            String dsName = (String) params.get("dsName");
            String sql = (String) params.get("sql");
            Integer pageIndex = params.get("pageIndex") != null
                    ? Integer.valueOf(params.get("pageIndex").toString()) : null;
            Integer pageSize = params.get("pageSize") != null
                    ? Integer.valueOf(params.get("pageSize").toString()) : null;
            log.info("数据查询审计 | 数据源={} | 页码={} | SQL={}", dsName, pageIndex, sql);
            DataQueryService.QueryResult result = dataQueryService.executeQuery(dsName, sql, pageIndex, pageSize);
            return CommonResult.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("数据查询被拦截: {}", e.getMessage());
            return CommonResult.error(ErrorCode.PARAMETER_ILLEGAL_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("SQL 查询失败: {}", e.getMessage(), e);
            return CommonResult.error(ErrorCode.DATABASE_EXCEPTION, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出查询结果为 Excel
     *
     * @param params   含 dsName（数据源名称）、sql（查询SQL）
     * @param response HTTP 响应
     */
    @Operation(summary = "导出Excel")
    @PostMapping("/export/excel")
    public void exportExcel(@RequestBody Map<String, Object> params, HttpServletResponse response) {
        String dsName = (String) params.get("dsName");
        String sql = (String) params.get("sql");
        String fileName = "query_result_" + System.currentTimeMillis() + ".xlsx";
        try {
            log.info("Excel 导出审计 | 数据源={} | SQL={}", dsName, sql);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            dataQueryService.exportExcel(dsName, sql, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            log.warn("Excel 导出被拦截: {}", e.getMessage());
            writeErrorResponse(response, e.getMessage());
        } catch (Exception e) {
            log.error("Excel 导出失败: {}", e.getMessage(), e);
            writeErrorResponse(response, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出查询结果为 CSV
     *
     * @param params   含 dsName（数据源名称）、sql（查询SQL）
     * @param response HTTP 响应
     */
    @Operation(summary = "导出CSV")
    @PostMapping("/export/csv")
    public void exportCsv(@RequestBody Map<String, Object> params, HttpServletResponse response) {
        String dsName = (String) params.get("dsName");
        String sql = (String) params.get("sql");
        String fileName = "query_result_" + System.currentTimeMillis() + ".csv";
        try {
            log.info("CSV 导出审计 | 数据源={} | SQL={}", dsName, sql);
            response.setContentType("text/csv");
            response.setCharacterEncoding("UTF-8");
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            dataQueryService.exportCsv(dsName, sql, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            log.warn("CSV 导出被拦截: {}", e.getMessage());
            writeErrorResponse(response, e.getMessage());
        } catch (Exception e) {
            log.error("CSV 导出失败: {}", e.getMessage(), e);
            writeErrorResponse(response, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出失败时写入错误信息到响应
     */
    private void writeErrorResponse(HttpServletResponse response, String message) {
        try {
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"" + ErrorCode.PARAMETER_ILLEGAL_ERROR + "\",\"message\":\"" + message + "\"}");
        } catch (Exception ignored) {
            // 忽略
        }
    }
}
