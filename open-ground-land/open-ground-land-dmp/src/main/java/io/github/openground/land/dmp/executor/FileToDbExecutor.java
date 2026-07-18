package io.github.openground.land.dmp.executor;

import io.github.openground.base.exception.CommonException;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.mapper.SysDatasourceMapper;
import io.github.openground.common.datasource.service.TableMetadataService;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 文件→库执行器
 * <p>读取文件（分隔符格式），解析列映射，批量写入目标数据库。
 * 采用生产者-消费者模型（BlockingQueue），读文件与写数据库并行执行。</p>
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Component
public class FileToDbExecutor {

    private static final int QUEUE_CAPACITY = 10000;

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private SysDatasourceMapper sysDatasourceMapper;

    @Autowired
    private TableMetadataService tableMetadataService;

    /**
     * 执行文件入库
     *
     * @param config 任务配置
     * @return 执行结果（含成功行数、失败行数、错误文件路径）
     */
    public ExecuteResult execute(DmpDataExchangeConfig config) {
        String filePath = normalizePath(DatePathResolver.resolve(config.getSourceFilePath()));
        String targetTable = config.getTargetTable();
        String delimiter = config.getFileDelimiter() != null ? config.getFileDelimiter() : "|";
        String encoding = config.getFileEncoding() != null ? config.getFileEncoding() : "UTF-8";
        int batchSize = config.getBatchSize() != null && config.getBatchSize() > 0 ? config.getBatchSize() : 2000;
        int threadCount = config.getThreadCount() != null && config.getThreadCount() > 0
                ? config.getThreadCount() : 1;

        List<ColumnMapping> columns = parseColumnMappings(config.getColumnMappings());
        if (columns.isEmpty()) {
            // 未配置列映射时，自动按列顺序映射到目标表
            log.info("列映射未配置，自动从目标表获取字段列表: table={}", targetTable);
            columns = autoGenerateColumnMappings(config.getTargetDsId(), targetTable);
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("列映射配置不能为空，且无法自动生成（目标表可能不存在或无可用字段）");
        }

        JdbcTemplate jdbcTemplate = resolveTargetJdbcTemplate(config.getTargetDsId());

        // TRUNCATE 模式下先清空目标表
        if ("TRUNCATE".equals(config.getWriteMode())) {
            log.info("TRUNCATE 模式：清空目标表 {}", targetTable);
            jdbcTemplate.execute("TRUNCATE TABLE " + targetTable);
        }

        return doParallelExecute(filePath, delimiter, encoding, jdbcTemplate, targetTable, columns, batchSize, threadCount);
    }

    /**
     * 执行结果封装
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

    /**
     * 生产者-消费者并行执行
     */
    private ExecuteResult doParallelExecute(String filePath, String delimiter, String encoding,
                                  JdbcTemplate jdbcTemplate, String targetTable,
                                  List<ColumnMapping> columns, int batchSize, int threadCount) {
        BlockingQueue<String[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        AtomicLong totalRows = new AtomicLong(0);
        AtomicLong errorRows = new AtomicLong(0);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // 错误日志文件：xxx.csv → xxx.csv.err
        String errorLogPath = filePath + ".err";

        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(new DbWriterTask(jdbcTemplate, targetTable, columns,
                    batchSize, queue, doneLatch, totalRows, errorRows,
                    errorLogPath, delimiter), "db-writer-" + i);
            worker.start();
        }

        long lineCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lineCount++;
                String[] fields = line.split(Pattern.quote(delimiter), -1);
                queue.put(fields);
            }
        } catch (Exception e) {
            log.error("文件读取异常: file={}, 已读取行数={}", filePath, lineCount, e);
            throw new RuntimeException("文件入库失败: " + e.getMessage(), e);
        } finally {
            for (int i = 0; i < threadCount; i++) {
                try {
                    queue.put(POISON);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("消费者线程等待被中断", e);
        }

        long processed = totalRows.get();
        long errors = errorRows.get();
        log.info("文件入库完成: file={}, table={}, 处理行={}, 失败行={}, 错误日志={}",
                filePath, targetTable, processed, errors,
                errors > 0 ? errorLogPath : "无");
        return new ExecuteResult((int) processed, (int) errors, errors > 0 ? errorLogPath : null);
    }

    private static final String[] POISON = new String[0];

    /**
     * 数据库写入任务（消费者）
     *
     * @since 1.0.5
     */
    private static class DbWriterTask implements Runnable {
        private final JdbcTemplate jdbcTemplate;
        private final String targetTable;
        private final List<ColumnMapping> columns;
        private final int batchSize;
        private final BlockingQueue<String[]> queue;
        private final CountDownLatch doneLatch;
        private final AtomicLong totalRows;
        private final AtomicLong errorRows;
        private final String errorLogPath;
        private final String delimiter;

        DbWriterTask(JdbcTemplate jdbcTemplate, String targetTable,
                     List<ColumnMapping> columns, int batchSize,
                     BlockingQueue<String[]> queue, CountDownLatch doneLatch,
                     AtomicLong totalRows, AtomicLong errorRows,
                     String errorLogPath, String delimiter) {
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
            List<String[]> batch = new ArrayList<>(batchSize);
            long localTotal = 0;
            long localErrors = 0;

            try {
                while (true) {
                    String[] fields = queue.take();
                    if (fields == POISON) {
                        if (!batch.isEmpty()) {
                            localTotal += batchInsertWithRetry(batch);
                            batch.clear();
                        }
                        break;
                    }

                    batch.add(fields);
                    if (batch.size() >= batchSize) {
                        localTotal += batchInsertWithRetry(batch);
                        batch.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!batch.isEmpty()) {
                    try {
                        localTotal += batchInsertWithRetry(batch);
                    } catch (Exception ex) {
                        localErrors += batch.size();
                        log.error("消费者中断退出时批次写入失败", ex);
                    }
                    batch.clear();
                }
            } catch (Exception e) {
                log.error("消费者线程未捕获异常", e);
                localErrors += batch.size();
            }

            totalRows.addAndGet(localTotal);
            errorRows.addAndGet(localErrors);
            doneLatch.countDown();
        }

        /**
         * 批次写入 + 失败逐条重试 + 错误日志
         * <p>整批失败时回退到逐条插入，失败的单行写入 .err 错误日志文件。</p>
         *
         * @since 1.0.5
         */
        private int batchInsertWithRetry(List<String[]> batch) {
            try {
                return doBatchInsert(batch);
            } catch (Exception e) {
                log.warn("批次写入失败，逐条重试: table={}, batchSize={}", targetTable, batch.size());
                int success = 0;
                int failed = 0;
                for (String[] fields : batch) {
                    try {
                        doBatchInsert(Collections.singletonList(fields));
                        success++;
                    } catch (Exception e2) {
                        failed++;
                        log.error("单行写入失败: fields=[{}], error={}",
                                String.join(delimiter, fields), e2.getMessage());
                        try (FileWriter fw = new FileWriter(errorLogPath, true);
                             BufferedWriter bw = new BufferedWriter(fw);
                             PrintWriter pw = new PrintWriter(bw)) {
                            pw.println("[ERROR] " + String.join(delimiter, fields)
                                    + " | " + e2.getMessage());
                        } catch (IOException ioe) {
                            log.error("写入错误日志文件失败: {}", errorLogPath, ioe);
                        }
                    }
                }
                if (failed > 0) {
                    errorRows.addAndGet(failed);
                }
                log.info("逐条重试完成: table={}, 成功={}, 失败={}", targetTable, success, failed);
                return success;
            }
        }

        /**
         * @since 1.0.5
         */
        private int doBatchInsert(List<String[]> batch) {
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

            List<Object[]> batchArgs = new ArrayList<>(batch.size());
            for (String[] fields : batch) {
                Object[] args = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMapping cm = columns.get(i);
                    int index = cm.getFileIndex();
                    String val = (index >= 0 && index < fields.length) ? fields[index] : null;
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
                    // MySQL rewriteBatchedStatements=true 返回 -2，表示写入成功但行数未知
                    // 对于 INSERT，每行成功即为 1 条
                    count += 1;
                }
            }
            return count;
        }
    }

    /**
     * 解析目标数据源 ID → dsName → JdbcTemplate
     * <p>数据源不存在时直接报错，不静默降级。</p>
     *
     * @since 1.0.5
     */
    private JdbcTemplate resolveTargetJdbcTemplate(Long targetDsId) {
        if (targetDsId == null) {
            return dynamicJdbcTemplate.getJdbcTemplate();
        }
        SysDatasourceDO ds = sysDatasourceMapper.selectById(targetDsId);
        if (ds == null || ds.getDsName() == null) {
            throw new CommonException("514003",
                    "目标数据源不存在: id=" + targetDsId + "，请检查配置");
        }
        return dynamicJdbcTemplate.getJdbcTemplate(ds.getDsName());
    }

    private List<ColumnMapping> parseColumnMappings(String columnMappingsJson) {
        if (columnMappingsJson == null || columnMappingsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(columnMappingsJson,
                    mapper.getTypeFactory().constructCollectionType(List.class, ColumnMapping.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("列映射JSON解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 自动按列顺序生成映射：fileIndex=0→第一列, 1→第二列...
     *
     * @since 1.0.5
     */
    private List<ColumnMapping> autoGenerateColumnMappings(Long targetDsId, String targetTable) {
        SysDatasourceDO ds = sysDatasourceMapper.selectById(targetDsId);
        if (ds == null || ds.getDsName() == null) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> columns = tableMetadataService.getTableColumns(ds.getDsName(), targetTable);
            List<ColumnMapping> result = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                Map<String, Object> col = columns.get(i);
                ColumnMapping cm = new ColumnMapping();
                cm.setFileIndex(i);
                cm.setTargetColumn((String) col.get("columnName"));
                cm.setTargetType((String) col.get("typeName"));
                result.add(cm);
            }
            log.info("自动生成列映射: {} 列", result.size());
            return result;
        } catch (Exception e) {
            log.warn("自动生成列映射失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 统一路径分隔符为 /，兼容 Windows 反斜杠
     */
    private static String normalizePath(String path) {
        if (path == null) return null;
        return path.replace("\\", "/");
    }

    private static Object convertValue(String val, String targetType) {
        if (val == null || val.isEmpty()) return null;
        String trimmed = val.trim();

        // 数值类型
        if ("INTEGER".equalsIgnoreCase(targetType) || "INT".equalsIgnoreCase(targetType)
                || "BIGINT".equalsIgnoreCase(targetType) || "NUMBER".equalsIgnoreCase(targetType)
                || "SMALLINT".equalsIgnoreCase(targetType) || "TINYINT".equalsIgnoreCase(targetType)) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "数值转换失败: 值='" + val + "', 目标类型=" + targetType, e);
            }
        }
        if ("DECIMAL".equalsIgnoreCase(targetType) || "FLOAT".equalsIgnoreCase(targetType)
                || "DOUBLE".equalsIgnoreCase(targetType) || "NUMERIC".equalsIgnoreCase(targetType)) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "数值转换失败: 值='" + val + "', 目标类型=" + targetType, e);
            }
        }

        // 日期/时间类型：自动识别常见格式
        if ("DATE".equalsIgnoreCase(targetType)) {
            return parseDate(trimmed);
        }
        if ("DATETIME".equalsIgnoreCase(targetType) || "TIMESTAMP".equalsIgnoreCase(targetType)) {
            return parseTimestamp(trimmed);
        }

        // 字符串类型：去前后空格后返回
        return trimmed;
    }

    /**
     * 尝试常见日期格式解析
     */
    private static java.sql.Date parseDate(String val) {
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "yyyy-MM", "yyyyMM"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern).parse(val);
                return new java.sql.Date(d.getTime());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("日期转换失败: 值='" + val + "'，支持的格式:"
                + " yyyy-MM-dd, yyyy/MM/dd, yyyyMMdd");
    }

    /**
     * 尝试常见时间戳格式解析
     */
    private static Timestamp parseTimestamp(String val) {
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyyMMddHHmmss",
                "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern).parse(val);
                return new Timestamp(d.getTime());
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("时间戳转换失败: 值='" + val + "'，支持的格式:"
                + " yyyy-MM-dd HH:mm:ss, yyyyMMddHHmmss");
    }

    public static class ColumnMapping {
        private String sourceColumn;
        private String targetColumn;
        private int fileIndex;
        private String sourceType;
        private String targetType;

        public String getSourceColumn() {
            return sourceColumn;
        }

        public void setSourceColumn(String sourceColumn) {
            this.sourceColumn = sourceColumn;
        }

        public String getTargetColumn() {
            return targetColumn;
        }

        public void setTargetColumn(String targetColumn) {
            this.targetColumn = targetColumn;
        }

        public int getFileIndex() {
            return fileIndex;
        }

        public void setFileIndex(int fileIndex) {
            this.fileIndex = fileIndex;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }
    }
}
