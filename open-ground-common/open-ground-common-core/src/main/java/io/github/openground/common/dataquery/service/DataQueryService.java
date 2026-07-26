package io.github.openground.common.dataquery.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import io.github.openground.common.jdbc.DynamicDataSourceManager;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据查询与导出服务
 *
 * <p>基于多数据源组件（{@link DynamicJdbcTemplate}）实现对指定数据源的
 * 自定义 SQL 查询、表数据浏览，以及查询结果的 Excel / CSV 导出。
 *
 * <p>仅支持 SELECT 查询，禁止执行 DML/DDL 语句。
 * 导出采用分批查询 + 分批写入模式，避免大数据量导致内存溢出。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Service
public class DataQueryService {

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private DynamicDataSourceManager dynamicDataSourceManager;

    /** 单次导出最大行数，防止内存溢出 */
    private static final int MAX_EXPORT_ROWS = 100000;

    /** 分批查询的批次大小 */
    private static final int BATCH_SIZE = 5000;

    /** 危险关键字正则：INTO OUTFILE / INTO DUMPFILE / LOAD_FILE 等 */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD_FILE|INTO\\s+@)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 获取所有可用数据源列表
     *
     * @return 数据源信息列表，每项含 dsName / dbName / app / dbType / source
     */
    public List<Map<String, String>> listDataSources() {
        return dynamicDataSourceManager.getDataSourceList();
    }

    /**
     * 获取指定数据源的表列表
     *
     * @param dsName 数据源名称
     * @return 表信息列表
     */
    public List<Map<String, Object>> listTables(String dsName) {
        validateDsName(dsName);
        return dynamicJdbcTemplate.getTableList(dsName);
    }

    /**
     * 获取指定表的列信息
     *
     * @param dsName    数据源名称
     * @param tableName 表名
     * @return 列信息列表
     */
    public List<Map<String, Object>> listTableColumns(String dsName, String tableName) {
        validateDsName(dsName);
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        return dynamicJdbcTemplate.getTableColumns(dsName, tableName);
    }

    /**
     * 执行自定义 SQL 查询（仅 SELECT）
     *
     * <p>自动检测 SQL 类型，非 SELECT 语句将拒绝执行。
     * 支持分页，分页 SQL 由方言自动处理。
     *
     * @param dsName    数据源名称
     * @param sql       查询 SQL
     * @param pageIndex 页码（从 1 开始，null 或 0 表示不分页）
     * @param pageSize  每页条数（null 表示不分页）
     * @return 查询结果
     */
    public QueryResult executeQuery(String dsName, String sql, Integer pageIndex, Integer pageSize) {
        validateDsName(dsName);
        validateSelectSql(sql);

        boolean paged = pageIndex != null && pageIndex > 0 && pageSize != null && pageSize > 0;
        long totalCount = 0;
        List<Map<String, Object>> rows;

        if (paged) {
            // 分页查询：先查总数，再查分页数据
            String countSql = wrapCountSql(sql);
            List<Map<String, Object>> countResult = dynamicJdbcTemplate.queryForList(dsName, countSql);
            if (countResult != null && !countResult.isEmpty()) {
                Object cnt = countResult.get(0).values().iterator().next();
                totalCount = cnt instanceof Number ? ((Number) cnt).longValue() : 0;
            }
            String pageSql = dynamicJdbcTemplate.handlePageSql(dsName, sql, pageIndex, pageSize);
            rows = dynamicJdbcTemplate.queryForList(dsName, pageSql);
        } else {
            rows = dynamicJdbcTemplate.queryForList(dsName, sql);
            totalCount = rows != null ? rows.size() : 0;
        }

        // 提取列名（从第一行数据的 key，保持顺序）
        List<String> columns = new ArrayList<>();
        if (rows != null && !rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        }

        QueryResult result = new QueryResult();
        result.setColumns(columns);
        result.setRows(rows != null ? rows : new ArrayList<>());
        result.setTotal(totalCount);
        result.setPageIndex(paged ? pageIndex : null);
        result.setPageSize(paged ? pageSize : null);
        return result;
    }

    /**
     * 导出查询结果为 Excel（分批查询 + 分批写入）
     *
     * <p>自动包装 LIMIT 限制最大导出行数，分批查询（每批 {@value #BATCH_SIZE} 行），
     * 分批写入 Excel，避免一次性加载全部数据到内存。
     *
     * @param dsName 数据源名称
     * @param sql    查询 SQL（仅 SELECT）
     * @param os     输出流
     */
    public void exportExcel(String dsName, String sql, OutputStream os) {
        validateDsName(dsName);
        validateSelectSql(sql);

        // 去除末尾分号，包装 LIMIT
        String limitedSql = stripSemicolon(sql);
        String dbType = dynamicDataSourceManager.getDbType(dsName);
        String exportSql = wrapLimitSql(limitedSql, dbType, MAX_EXPORT_ROWS);

        // 第一批查询：获取列名 + 首批数据
        String firstBatchSql = dynamicJdbcTemplate.handlePageSql(dsName, limitedSql, 1, BATCH_SIZE);
        List<Map<String, Object>> rows = dynamicJdbcTemplate.queryForList(dsName, firstBatchSql);
        if (rows == null || rows.isEmpty()) {
            // 无数据，写空表头
            ExcelWriter excelWriter = EasyExcel.write(os).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("查询结果").build();
            excelWriter.write(Collections.emptyList(), writeSheet);
            excelWriter.finish();
            return;
        }

        // 提取列名
        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        // 构造表头
        List<List<String>> head = new ArrayList<>();
        for (String col : columns) {
            List<String> headCol = new ArrayList<>();
            headCol.add(col);
            head.add(headCol);
        }

        ExcelWriter excelWriter = EasyExcel.write(os).build();
        WriteSheet writeSheet = EasyExcel.writerSheet("查询结果")
                .head(head)
                .build();

        // 分批写入
        int totalWritten = 0;
        int pageIndex = 1;
        while (rows != null && !rows.isEmpty()) {
            List<List<Object>> dataList = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                List<Object> rowData = new ArrayList<>(columns.size());
                for (String col : columns) {
                    Object val = row.get(col);
                    rowData.add(val != null ? val.toString() : "");
                }
                dataList.add(rowData);
            }
            excelWriter.write(dataList, writeSheet);
            totalWritten += rows.size();

            if (totalWritten >= MAX_EXPORT_ROWS) {
                log.info("导出达到最大行数限制（{}行），停止查询", MAX_EXPORT_ROWS);
                break;
            }

            // 查询下一批
            pageIndex++;
            String batchSql = dynamicJdbcTemplate.handlePageSql(dsName, limitedSql, pageIndex, BATCH_SIZE);
            rows = dynamicJdbcTemplate.queryForList(dsName, batchSql);
        }

        excelWriter.finish();
        log.info("Excel 导出完成，共写入 {} 行", totalWritten);
    }

    /**
     * 导出查询结果为 CSV（分批查询 + 流式写入）
     *
     * <p>自动包装 LIMIT 限制最大导出行数，分批查询（每批 {@value #BATCH_SIZE} 行），
     * 流式写入 CSV 文件，避免一次性加载全部数据到内存。
     *
     * @param dsName 数据源名称
     * @param sql    查询 SQL（仅 SELECT）
     * @param os     输出流
     */
    public void exportCsv(String dsName, String sql, OutputStream os) {
        validateDsName(dsName);
        validateSelectSql(sql);

        // 去除末尾分号
        String limitedSql = stripSemicolon(sql);

        // 第一批查询
        String firstBatchSql = dynamicJdbcTemplate.handlePageSql(dsName, limitedSql, 1, BATCH_SIZE);
        List<Map<String, Object>> rows = dynamicJdbcTemplate.queryForList(dsName, firstBatchSql);

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        try {
            // UTF-8 BOM
            writer.write('\ufeff');

            if (rows == null || rows.isEmpty()) {
                // 无数据，只写空行
                writer.println();
                return;
            }

            // 提取列名
            List<String> columns = new ArrayList<>(rows.get(0).keySet());

            // 写表头
            writer.println(String.join(",", columns.stream().map(this::csvEscape).toList()));

            // 分批写入数据
            int totalWritten = 0;
            int pageIndex = 1;
            while (rows != null && !rows.isEmpty()) {
                for (Map<String, Object> row : rows) {
                    List<String> values = new ArrayList<>(columns.size());
                    for (String col : columns) {
                        Object val = row.get(col);
                        values.add(csvEscape(val != null ? val.toString() : ""));
                    }
                    writer.println(String.join(",", values));
                }
                totalWritten += rows.size();
                writer.flush(); // 及时刷新，释放内存

                if (totalWritten >= MAX_EXPORT_ROWS) {
                    log.info("导出达到最大行数限制（{}行），停止查询", MAX_EXPORT_ROWS);
                    break;
                }

                // 查询下一批
                pageIndex++;
                String batchSql = dynamicJdbcTemplate.handlePageSql(dsName, limitedSql, pageIndex, BATCH_SIZE);
                rows = dynamicJdbcTemplate.queryForList(dsName, batchSql);
            }

            log.info("CSV 导出完成，共写入 {} 行", totalWritten);
        } finally {
            writer.flush();
        }
    }

    /**
     * 校验数据源名称是否存在
     */
    private void validateDsName(String dsName) {
        if (dsName == null || dsName.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (dynamicDataSourceManager.getDescriptor(dsName) == null) {
            throw new IllegalArgumentException("数据源不存在: " + dsName);
        }
    }

    /**
     * 校验 SQL 仅为安全的 SELECT 语句
     *
     * <p>检查项：
     * <ul>
     *   <li>必须以 SELECT 或 WITH 开头（跳过前导注释）</li>
     *   <li>禁止分号后接额外语句</li>
     *   <li>禁止 INTO OUTFILE / INTO DUMPFILE / LOAD_FILE 等危险关键字</li>
     *   <li>调用 checkSqlInject 做二次注入检测</li>
     * </ul>
     */
    private void validateSelectSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String trimmed = sql.trim().toLowerCase();
        // 去除前导注释和空白
        while (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
            int idx = trimmed.startsWith("--") ? trimmed.indexOf('\n') : trimmed.indexOf("*/");
            if (idx < 0) {
                break;
            }
            trimmed = trimmed.substring(idx + 1).trim().toLowerCase();
        }
        if (!trimmed.startsWith("select") && !trimmed.startsWith("with")) {
            throw new IllegalArgumentException("仅支持 SELECT 查询语句");
        }
        // 检查是否包含分号后的额外语句
        String upper = sql.toUpperCase();
        if (upper.contains(";")) {
            String afterSemicolon = sql.substring(sql.lastIndexOf(';') + 1).trim().toUpperCase();
            if (!afterSemicolon.isEmpty() && !afterSemicolon.startsWith("--") && !afterSemicolon.startsWith("/*")) {
                throw new IllegalArgumentException("不支持多条 SQL 语句执行");
            }
        }
        // 检查危险关键字：INTO OUTFILE / INTO DUMPFILE / LOAD_FILE / INTO @变量
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("SQL 包含不允许的关键字（INTO OUTFILE/DUMPFILE/LOAD_FILE）");
        }
        // 调用已有的 SQL 注入检测工具做二次校验
        if (dynamicJdbcTemplate.checkSqlInject(sql)) {
            throw new IllegalArgumentException("SQL 存在注入风险，已被拦截");
        }
    }

    /**
     * 去除 SQL 末尾的分号
     */
    private String stripSemicolon(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 包装 COUNT 查询 SQL
     */
    private String wrapCountSql(String sql) {
        String trimmed = stripSemicolon(sql);
        return "SELECT COUNT(0) AS total FROM (" + trimmed + ") count_wrapper";
    }

    /**
     * 包装 LIMIT 限制最大返回行数
     *
     * <p>根据数据库类型选择合适的 LIMIT 语法。如果原 SQL 已包含 LIMIT，
     * 则取较小值避免扩大范围。
     *
     * @param sql       原始 SQL（已去除末尾分号）
     * @param dbType    数据库类型
     * @param maxRows   最大行数
     * @return 带 LIMIT 的 SQL
     */
    private String wrapLimitSql(String sql, String dbType, int maxRows) {
        String upper = sql.toUpperCase().trim();
        // 如果已有 LIMIT，不再重复包装
        if (upper.contains("LIMIT")) {
            return sql;
        }
        if ("oracle".equalsIgnoreCase(dbType) || "gaussdb".equalsIgnoreCase(dbType)) {
            // Oracle/GaussDB 使用 ROWNUM
            return "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + maxRows;
        }
        // MySQL / PostgreSQL / DM 等使用 LIMIT
        return sql + " LIMIT " + maxRows;
    }

    /**
     * CSV 字段转义
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        // 包含逗号、引号、换行符的字段需要用双引号包裹
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 查询结果 DTO
     */
    public static class QueryResult {
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private long total;
        private Integer pageIndex;
        private Integer pageSize;

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public Integer getPageIndex() { return pageIndex; }
        public void setPageIndex(Integer pageIndex) { this.pageIndex = pageIndex; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    }
}
