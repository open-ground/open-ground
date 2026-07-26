package io.github.openground.land.dmp.executor;

import io.github.openground.base.exception.CommonException;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.mapper.SysDatasourceMapper;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.common.jdbc.SqlUtils;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 库→库执行器
 * <p>跨数据源数据库间数据迁移。从源数据库分页读取，经列映射+类型转换后批量写入目标数据库。
 * 支持 FULL_TABLE / CONDITIONAL / CUSTOM_SQL 三种导出模式。采用生产者-消费者模型并行执行。</p>
 *
 * @author open-ground
 * @since 1.0.7
 */
@Slf4j
@Component
public class DbToDbExecutor {

    private static final int QUEUE_CAPACITY = 50; // 队列中最多缓存 50 页

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private SysDatasourceMapper sysDatasourceMapper;

    /**
     * 执行库→库同步
     *
     * @param config 任务配置
     * @return 执行结果
     */
    public ExecuteResult execute(TaskDataExchangeConfig config) {
        SysDatasourceDO sourceDs = resolveDataSource(config.getSourceDsId(), "源");
        SysDatasourceDO targetDs = resolveDataSource(config.getTargetDsId(), "目标");
        String sourceDsName = sourceDs.getDsName();
        String targetDsName = targetDs.getDsName();
        String targetTable = config.getTargetTable();

        // 构建源端查询 SQL
        String sql = buildQuerySql(config);
        log.info("库→库: sourceDs={}, targetDs={}, targetTable={}, sql={}",
                sourceDsName, targetDsName, targetTable, sql);

        // 解析列映射
        List<ColumnMapping> columns = resolveColumnMappings(config, sourceDsName, sql);
        if (columns.isEmpty() && config.getColumnMappings() == null) {
            throw new IllegalArgumentException("列映射配置不能为空，且无法自动生成（源表可能为空或无可用字段）");
        }

        // TRUNCATE 模式下先清空目标表
        JdbcTemplate targetJdbc = dynamicJdbcTemplate.getJdbcTemplate(targetDsName);
        if ("TRUNCATE".equals(config.getWriteMode())) {
            log.info("TRUNCATE 模式：清空目标表 {}", targetTable);
            targetJdbc.execute("TRUNCATE TABLE " + targetTable);
        }

        // 并行执行
        int batchSize = config.getBatchSize() != null && config.getBatchSize() > 0 ? config.getBatchSize() : 2000;
        int threadCount = config.getThreadCount() != null && config.getThreadCount() > 0
                ? config.getThreadCount() : 1;
        String delimiter = config.getFileDelimiter() != null ? config.getFileDelimiter() : "|";

        return doParallelSync(sourceDsName, targetDsName, targetTable, sql,
                columns, batchSize, threadCount, delimiter, config);
    }

    /**
     * 根据导出模式构建源端查询 SQL
     */
    private String buildQuerySql(TaskDataExchangeConfig config) {
        String mode = config.getExportMode();
        String table = config.getSourceTable();
        String sourceQuery = config.getSourceQuery();

        // 自定义 SQL
        if ("CUSTOM_SQL".equals(mode) || "CUSTOM".equals(mode)) {
            if (sourceQuery == null || sourceQuery.trim().isEmpty()) {
                throw new IllegalArgumentException("自定义SQL模式下 sourceQuery 不能为空");
            }
            return DatePathResolver.resolve(sourceQuery.trim());
        }

        // 整表导出 / 条件导出
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("库→库模式下源表名不能为空");
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(table);

        if ("CONDITIONAL".equals(mode) && sourceQuery != null && !sourceQuery.trim().isEmpty()) {
            sql.append(" WHERE ").append(DatePathResolver.resolve(sourceQuery.trim()));
        }

        return sql.toString();
    }

    /**
     * 生产者-消费者并行同步
     */
    private ExecuteResult doParallelSync(String sourceDsName, String targetDsName, String targetTable,
                                          String sql, List<ColumnMapping> columns,
                                          int batchSize, int threadCount, String delimiter,
                                          TaskDataExchangeConfig config) {
        // 存储错误信息
        AtomicLong totalRows = new AtomicLong(0);
        AtomicLong errorRows = new AtomicLong(0);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        BlockingQueue<List<Map<String, Object>>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        // 错误日志文件
        String errorLogPath = config.getSourceFilePath() != null
                ? config.getSourceFilePath() + ".err"
                : "dmp_db2db_" + config.getId() + ".err";

        // 启动消费者线程
        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(new DbWriterTask(
                    dynamicJdbcTemplate.getJdbcTemplate(targetDsName),
                    targetTable, columns, batchSize, queue, doneLatch,
                    totalRows, errorRows, errorLogPath, delimiter),
                    "db2db-writer-" + i);
            worker.start();
        }

        // 生产者：分页读取源库
        long totalRead = 0;
        int page = 1;
        try {
            while (true) {
                String pageSql = dynamicJdbcTemplate.handlePageSql(sourceDsName, sql, page, batchSize);
                List<Map<String, Object>> rows = dynamicJdbcTemplate.queryForList(sourceDsName, pageSql);
                if (rows == null || rows.isEmpty()) break;

                queue.put(rows);
                totalRead += rows.size();

                if (rows.size() < batchSize) break; // 最后一页
                page++;
            }
        } catch (Exception e) {
            log.error("源库分页查询异常: page={}, sql={}", page, sql, e);
            throw new RuntimeException("库→库同步失败（源库读取异常）: " + e.getMessage(), e);
        } finally {
            // 放入 poison 信号通知消费者结束
            for (int i = 0; i < threadCount; i++) {
                try {
                    queue.put(POISON);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 等待消费者完成
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("消费者线程等待被中断", e);
        }

        long processed = totalRows.get();
        long errors = errorRows.get();
        log.info("库→库同步完成: source={}, target={}, 读取={}, 写入={}, 失败={}, 错误日志={}",
                sourceDsName, targetTable, totalRead, processed, errors,
                errors > 0 ? errorLogPath : "无");
        return new ExecuteResult((int) processed, (int) errors, errors > 0 ? errorLogPath : null);
    }

    @SuppressWarnings("unchecked")
    private static final List<Map<String, Object>> POISON = (List<Map<String, Object>>) Collections.EMPTY_LIST;

    /**
     * 数据库写入任务（消费者）
     */
    private static class DbWriterTask implements Runnable {
        private final JdbcTemplate jdbcTemplate;
        private final String targetTable;
        private final List<ColumnMapping> columns;
        private final int batchSize;
        private final BlockingQueue<List<Map<String, Object>>> queue;
        private final CountDownLatch doneLatch;
        private final AtomicLong totalRows;
        private final AtomicLong errorRows;
        private final String errorLogPath;
        private final String delimiter;

        DbWriterTask(JdbcTemplate jdbcTemplate, String targetTable,
                     List<ColumnMapping> columns, int batchSize,
                     BlockingQueue<List<Map<String, Object>>> queue,
                     CountDownLatch doneLatch, AtomicLong totalRows,
                     AtomicLong errorRows, String errorLogPath, String delimiter) {
            this.jdbcTemplate = jdbcTemplate;
            this.targetTable = targetTable;
            this.columns = columns;
            this.batchSize = batchSize;
            this.queue = queue;
            this.doneLatch = doneLatch;
            this.totalRows = totalRows;
            this.errorRows = errorRows;
            this.errorLogPath = errorLogPath;
            this.delimiter = delimiter;
        }

        @Override
        public void run() {
            long localTotal = 0;
            try {
                while (true) {
                    List<Map<String, Object>> page = queue.take();
                    if (page == POISON) break;

                    localTotal += batchInsertWithRetry(page);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("消费者线程未捕获异常", e);
            }
            totalRows.addAndGet(localTotal);
            doneLatch.countDown();
        }

        /**
         * 批量写入 + 失败逐条重试 + 错误日志
         */
        private int batchInsertWithRetry(List<Map<String, Object>> rows) {
            try {
                return doBatchInsert(rows);
            } catch (Exception e) {
                int success = 0;
                int failed = 0;
                for (Map<String, Object> row : rows) {
                    try {
                        doBatchInsert(Collections.singletonList(row));
                        success++;
                    } catch (Exception e2) {
                        failed++;
                        log.error("单行写入失败: row={}, error={}",
                                row, e2.getMessage());
                        try (FileWriter fw = new FileWriter(errorLogPath, true);
                             BufferedWriter bw = new BufferedWriter(fw);
                             PrintWriter pw = new PrintWriter(bw)) {
                            pw.println("[ERROR] " + row + " | " + e2.getMessage());
                        } catch (IOException ioe) {
                            log.error("写入错误日志文件失败: {}", errorLogPath, ioe);
                        }
                    }
                }
                if (failed > 0) {
                    errorRows.addAndGet(failed);
                }
                log.info("逐条重试完成: 成功={}, 失败={}", success, failed);
                return success;
            }
        }

        private int doBatchInsert(List<Map<String, Object>> rows) {
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(targetTable).append(" (");

            StringBuilder values = new StringBuilder(" VALUES (");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                    values.append(", ");
                }
                sql.append(columns.get(i).getTargetColumn());
                values.append("?");
            }
            sql.append(")");
            values.append(")");
            sql.append(values);

            List<Object[]> batchArgs = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Object[] args = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMapping cm = columns.get(i);
                    Object val = row.get(cm.getSourceColumn());
                    args[i] = convertValue(val, cm.getTargetType());
                }
                batchArgs.add(args);
            }

            int[] results = jdbcTemplate.batchUpdate(sql.toString(), batchArgs);
            int count = 0;
            for (int r : results) {
                if (r > 0) {
                    count += r;
                } else if (r == Statement.SUCCESS_NO_INFO) {
                    count += 1;
                }
            }
            return count;
        }
    }

    // ====== 辅助方法 ======

    private SysDatasourceDO resolveDataSource(Long dsId, String label) {
        if (dsId == null) {
            throw new CommonException("514003", label + "数据源不能为空");
        }
        SysDatasourceDO ds = sysDatasourceMapper.selectById(dsId);
        if (ds == null || ds.getDsName() == null) {
            throw new CommonException("514003", label + "数据源不存在: id=" + dsId);
        }
        return ds;
    }

    /**
     * 解析列映射：优先使用配置的映射，否则从查询结果自动获取
     */
    private List<ColumnMapping> resolveColumnMappings(TaskDataExchangeConfig config,
                                                      String sourceDsName, String sql) {
        List<ColumnMapping> parsed = parseColumnMappings(config.getColumnMappings());
        if (!parsed.isEmpty()) {
            return parsed;
        }
        // 未配置映射时，从源表查询获取字段列表
        try {
            String firstPageSql = dynamicJdbcTemplate.handlePageSql(sourceDsName, sql, 1, 1);
            List<Map<String, Object>> sample = dynamicJdbcTemplate.queryForList(sourceDsName, firstPageSql);
            if (sample == null || sample.isEmpty()) {
                return Collections.emptyList();
            }
            Map<String, Object> firstRow = sample.get(0);
            List<ColumnMapping> auto = new ArrayList<>();
            for (String colName : firstRow.keySet()) {
                ColumnMapping cm = new ColumnMapping();
                cm.setSourceColumn(colName);
                cm.setTargetColumn(colName); // 自动映射同名
                cm.setSourceType(inferType(firstRow.get(colName)));
                cm.setTargetType(inferType(firstRow.get(colName)));
                auto.add(cm);
            }
            log.info("自动生成列映射: {} 列", auto.size());
            return auto;
        } catch (Exception e) {
            log.warn("自动生成列映射失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String inferType(Object value) {
        if (value == null) return "VARCHAR";
        if (value instanceof Long || value instanceof Integer) return "BIGINT";
        if (value instanceof Double || value instanceof Float) return "DECIMAL";
        if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp
                || value instanceof java.util.Date) return "TIMESTAMP";
        return "VARCHAR";
    }

    private List<ColumnMapping> parseColumnMappings(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, ColumnMapping.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("列映射JSON解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 类型转换：委托 {@link SqlUtils#convertValue}，将源库读出的 Java 值转为适配目标库 SQL 类型的值
     */
    private static Object convertValue(Object val, String targetType) {
        return SqlUtils.convertValue(val, targetType);
    }

    // ====== 结果封装 ======

    /**
     * 执行结果
     */
    public static class ExecuteResult {
        private final int successRows;
        private final int errorRows;
        private final String errorLogPath;

        ExecuteResult(int successRows, int errorRows, String errorLogPath) {
            this.successRows = successRows;
            this.errorRows = errorRows;
            this.errorLogPath = errorLogPath;
        }

        public int getSuccessRows() { return successRows; }
        public int getErrorRows() { return errorRows; }
        public String getErrorLogPath() { return errorLogPath; }
    }

    // ====== 列映射定义 ======

    public static class ColumnMapping {
        private String sourceColumn;
        private String targetColumn;
        private String sourceType;
        private String targetType;

        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
    }
}
