package io.github.openground.land.dmp.executor;

import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件→库执行器
 * <p>读取文件（分隔符格式），解析列映射，批量写入目标数据库</p>
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Component
public class FileToDbExecutor {

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    /**
     * 执行文件入库
     *
     * @param config 任务配置
     * @return 处理行数
     */
    public int execute(DmpDataExchangeConfig config) {
        String filePath = config.getSourceFilePath();
        String targetTable = config.getTargetTable();
        String delimiter = config.getFileDelimiter() != null ? config.getFileDelimiter() : "|";
        String encoding = config.getFileEncoding() != null ? config.getFileEncoding() : "UTF-8";
        int batchSize = config.getBatchSize() != null && config.getBatchSize() > 0 ? config.getBatchSize() : 2000;

        // 解析列映射
        List<ColumnMapping> columns = parseColumnMappings(config.getColumnMappings());
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("列映射配置不能为空");
        }

        int totalRows = 0;
        List<String[]> batch = new ArrayList<>(batchSize);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), encoding))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // 跳过空行和注释行
                }

                String[] fields = line.split(delimiter, -1);
                batch.add(fields);

                if (batch.size() >= batchSize) {
                    totalRows += batchInsert(targetTable, columns, batch);
                    batch.clear();
                }
            }

            // 最后一批
            if (!batch.isEmpty()) {
                totalRows += batchInsert(targetTable, columns, batch);
            }

        } catch (Exception e) {
            log.error("文件入库异常: file={}", filePath, e);
            throw new RuntimeException("文件入库失败: " + e.getMessage(), e);
        }

        log.info("文件入库完成: file={}, table={}, rows={}", filePath, targetTable, totalRows);
        return totalRows;
    }

    /**
     * 批量插入
     */
    private int batchInsert(String table, List<ColumnMapping> columns, List<String[]> batch) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table).append(" (");

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

        int[] results = dynamicJdbcTemplate.getJdbcTemplate().batchUpdate(sql.toString(), batchArgs);
        int count = 0;
        for (int r : results) {
            if (r > 0) count += r;
        }
        return count;
    }

    /**
     * 解析列映射 JSON
     */
    private List<ColumnMapping> parseColumnMappings(String columnMappingsJson) {
        // TODO: 解析 JSON → List<ColumnMapping>
        // 临时返回空，等待 JSON 解析逻辑完善
        return new ArrayList<>();
    }

    private Object convertValue(String val, String targetType) {
        if (val == null || val.isEmpty()) return null;
        // 按目标类型转换
        if ("INTEGER".equalsIgnoreCase(targetType) || "INT".equalsIgnoreCase(targetType)
                || "BIGINT".equalsIgnoreCase(targetType) || "NUMBER".equalsIgnoreCase(targetType)) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return val;
            }
        }
        if ("DECIMAL".equalsIgnoreCase(targetType) || "FLOAT".equalsIgnoreCase(targetType)
                || "DOUBLE".equalsIgnoreCase(targetType)) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return val;
            }
        }
        return val;
    }

    /**
     * 列映射定义
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
