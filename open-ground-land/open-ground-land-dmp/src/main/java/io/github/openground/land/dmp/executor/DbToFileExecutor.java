package io.github.openground.land.dmp.executor;

import io.github.openground.base.exception.CommonException;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.mapper.SysDatasourceMapper;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库→文件执行器
 * <p>从数据库查询数据，导出为分隔符文件（CSV/自定义分隔符）。
 * 支持整表导出、条件导出、自定义 SQL 三种模式。</p>
 *
 * @author open-ground
 * @since 1.0.5
 */
@Slf4j
@Component
public class DbToFileExecutor {

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private SysDatasourceMapper sysDatasourceMapper;

    /**
     * 执行库导出文件
     *
     * @param config 任务配置
     * @return 导出行数
     */
    public int execute(DmpDataExchangeConfig config) {
        String filePath = normalizePath(DatePathResolver.resolve(config.getTargetFilePath()));
        String delimiter = config.getFileDelimiter() != null ? config.getFileDelimiter() : "|";
        String encoding = config.getFileEncoding() != null ? config.getFileEncoding() : "UTF-8";
        int batchSize = config.getBatchSize() != null && config.getBatchSize() > 0 ? config.getBatchSize() : 2000;
        boolean headerEnabled = "1".equals(config.getHeaderEnabled());
        boolean doneEnabled = config.getDoneFileEnabled() == null || "1".equals(config.getDoneFileEnabled());

        // 解析数据源
        SysDatasourceDO ds = sysDatasourceMapper.selectById(config.getSourceDsId());
        if (ds == null || ds.getDsName() == null) {
            throw new CommonException("514003", "源数据源不存在: id=" + config.getSourceDsId());
        }
        String dsName = ds.getDsName();

        // 构建查询 SQL（含日期占位符渲染）
        String sql = buildQuerySql(config);
        log.info("库→文件: dsName={}, sql={}", dsName, sql);

        // 分页查询 + 写入文件
        String tmpPath = filePath + ".tmp";
        int totalRows;
        try {
            totalRows = doExport(dsName, sql, tmpPath, delimiter, encoding,
                    batchSize, headerEnabled, config);
        } catch (Exception e) {
            log.error("库导出文件异常: file={}", filePath, e);
            // 清理临时文件
            new File(tmpPath).delete();
            throw new RuntimeException("库导出文件失败: " + e.getMessage(), e);
        }

        // 重命名 .tmp → 正式文件
        File tmpFile = new File(tmpPath);
        File finalFile = new File(filePath);
        if (finalFile.exists() && !"APPEND".equals(config.getWriteMode())) {
            if (!finalFile.delete()) {
                log.warn("删除已存在的目标文件失败: {}", filePath);
            }
        }
        if (!tmpFile.renameTo(finalFile)) {
            log.error("临时文件重命名失败: {} → {}", tmpPath, filePath);
            throw new RuntimeException("临时文件重命名失败，导出结果不完整: " + filePath);
        }

        // 生成 .ok 标识文件
        if (doneEnabled) {
            createOkFile(filePath, totalRows, config.getId());
        }

        log.info("库导出文件完成: file={}, rows={}", filePath, totalRows);
        return totalRows;
    }

    /**
     * 根据导出模式构建 SQL
     */
    private String buildQuerySql(DmpDataExchangeConfig config) {
        String mode = config.getExportMode();
        String table = config.getTargetTable();
        String sourceQuery = config.getSourceQuery();

        // 自定义 SQL：直接使用，渲染日期占位符
        if ("CUSTOM_SQL".equals(mode) || "CUSTOM".equals(mode)) {
            if (sourceQuery == null || sourceQuery.trim().isEmpty()) {
                throw new IllegalArgumentException("自定义SQL模式下 sourceQuery 不能为空");
            }
            return DatePathResolver.resolve(sourceQuery.trim());
        }

        // 整表导出 / 条件导出
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("导出的目标表名不能为空");
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(table);

        if ("CONDITIONAL".equals(mode) && sourceQuery != null && !sourceQuery.trim().isEmpty()) {
            // 条件导出：WHERE 条件也支持日期占位符
            sql.append(" WHERE ").append(DatePathResolver.resolve(sourceQuery.trim()));
        }

        return sql.toString();
    }

    /**
     * 分页查询 + 写入临时文件
     */
    private int doExport(String dsName, String sql, String tmpPath, String delimiter,
                         String encoding, int batchSize, boolean headerEnabled,
                         DmpDataExchangeConfig config) throws Exception {

        List<ColumnMapping> columns = resolveColumnMappings(config, dsName, sql, batchSize);

        // 确保目标目录存在
        File parentDir = new File(tmpPath).getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            log.info("自动创建目录: {}", parentDir.getAbsolutePath());
            parentDir.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmpPath), encoding))) {

            // 输出表头
            if (headerEnabled) {
                String header = columns.stream()
                        .map(c -> c.getTargetColumn())
                        .collect(Collectors.joining(delimiter));
                writer.write(header);
                writer.newLine();
            }

            int page = 1;
            int total = 0;

            while (true) {
                String pageSql = dynamicJdbcTemplate.handlePageSql(dsName, sql, page, batchSize);
                List<Map<String, Object>> rows = dynamicJdbcTemplate.queryForList(dsName, pageSql);
                if (rows == null || rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    writeLine(writer, row, columns, delimiter);
                    total++;
                }

                // 如果返回行数小于 batchSize，说明是最后一页
                if (rows.size() < batchSize) break;
                page++;
            }

            return total;
        }
    }

    /**
     * 解析列映射：有配置则用配置，无配置则从查询结果自动获取
     */
    private List<ColumnMapping> resolveColumnMappings(DmpDataExchangeConfig config,
                                                       String dsName, String sql, int batchSize) {
        // 优先使用配置的列映射
        List<ColumnMapping> parsed = parseColumnMappings(config.getColumnMappings());
        if (!parsed.isEmpty()) {
            return parsed;
        }

        // 未配置列映射，从查询结果获取字段列表
        try {
            String firstPageSql = dynamicJdbcTemplate.handlePageSql(dsName, sql, 1, 1);
            List<Map<String, Object>> sample = dynamicJdbcTemplate.queryForList(dsName, firstPageSql);
            if (sample == null || sample.isEmpty()) {
                // 表为空，无法推断字段，但后续无数据可导出，返回空映射列表即可
                return Collections.emptyList();
            }

            Map<String, Object> firstRow = sample.get(0);
            List<ColumnMapping> auto = new ArrayList<>();
            int idx = 0;
            for (String colName : firstRow.keySet()) {
                ColumnMapping cm = new ColumnMapping();
                cm.setFileIndex(idx);
                cm.setTargetColumn(colName);
                cm.setTargetType(inferType(firstRow.get(colName)));
                auto.add(cm);
                idx++;
            }
            log.info("自动生成导出列映射: {} 列", auto.size());
            return auto;
        } catch (Exception e) {
            log.warn("自动生成导出列映射失败，将使用空映射: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 根据值推断数据类型
     */
    private String inferType(Object value) {
        if (value == null) return "VARCHAR";
        if (value instanceof Long || value instanceof Integer) return "BIGINT";
        if (value instanceof Double || value instanceof Float) return "DECIMAL";
        if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp
                || value instanceof java.util.Date) return "TIMESTAMP";
        return "VARCHAR";
    }

    /**
     * 写一行到文件
     */
    private void writeLine(BufferedWriter writer, Map<String, Object> row,
                           List<ColumnMapping> columns, String delimiter) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) line.append(delimiter);
            ColumnMapping cm = columns.get(i);
            Object value = row.get(cm.getTargetColumn());
            if (value != null) {
                String str = formatValue(value, cm.getTargetType(), delimiter);
                line.append(str);
            } else {
                // 列名在查询结果中不存在时记录日志
                log.warn("导出列 '{}' 在查询结果中不存在，跳过", cm.getTargetColumn());
            }
        }
        writer.write(line.toString());
        writer.newLine();
    }

    /**
     * 格式化输出值
     */
    private String formatValue(Object value, String targetType, String delimiter) {
        if (value == null) return "";
        String str = value.toString().trim();
        // 对于日期类型，统一格式
        if (value instanceof java.sql.Timestamp || value instanceof java.util.Date) {
            if ("DATE".equalsIgnoreCase(targetType)) {
                return new SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) value);
            }
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date) value);
        }
        // 如果值本身包含分隔符或换行，用双引号包裹
        if (str.contains(delimiter) || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * 创建 .ok 标识文件
     */
    private void createOkFile(String filePath, int rows, Long configId) {
        String okPath = filePath + ".ok";
        String checksum = md5Checksum(new File(filePath));
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String batchNo = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        try (PrintWriter pw = new PrintWriter(new FileWriter(okPath))) {
            pw.println("filename: " + new File(filePath).getName());
            pw.println("rows: " + rows);
            pw.println("checksum: MD5:" + checksum);
            pw.println("batchNo: " + batchNo);
            pw.println("createTime: " + now);
            pw.println("configId: " + configId);
        } catch (IOException e) {
            log.error("生成 .ok 文件失败: {}", okPath, e);
        }
    }

    /**
     * 计算文件 MD5
     */
    private String md5Checksum(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            log.warn("计算文件 MD5 失败: {}", file.getName());
            return "unknown";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** 统一路径分隔符为 /，兼容 Windows 反斜杠 */
    private static String normalizePath(String path) {
        if (path == null) return null;
        return path.replace("\\", "/");
    }

    // ====== 列映射解析（与 FileToDbExecutor 共享） ======

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
     * 列映射定义（与 FileToDbExecutor 共用）
     */
    public static class ColumnMapping {
        private String sourceColumn;
        private String targetColumn;
        private int fileIndex;
        private String sourceType;
        private String targetType;

        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
        public int getFileIndex() { return fileIndex; }
        public void setFileIndex(int fileIndex) { this.fileIndex = fileIndex; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
    }
}
