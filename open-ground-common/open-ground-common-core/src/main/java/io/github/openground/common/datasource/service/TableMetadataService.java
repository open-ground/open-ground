package io.github.openground.common.datasource.service;

import io.github.openground.base.exception.CommonException;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表元数据查询服务
 *
 * <p>通过 {@link DynamicJdbcTemplate} + {@link io.github.openground.common.jdbc.dialect.DbDialect}
 * 查询数据源的表列表和列信息，屏蔽各数据库差异。
 *
 * <p><b>设计说明：</b>独立为 Service 而非放在 SysDatasourceServiceImpl 中，
 * 是为了切断循环依赖链（SysDatasourceService → SysDatasourceProvider → DataSourceProviderRegistry
 * → DynamicDataSourceManager → DynamicJdbcTemplate → SysDatasourceService）。
 * DynamicJdbcTemplate 注入使用 {@link Lazy}，避免启动期触发生成链。
 *
 * @author open-ground
 * @since 1.0.5
 */
@Slf4j
@Service
public class TableMetadataService {

    @Autowired
    @Lazy
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    /**
     * 获取数据源的表列表
     *
     * @param dsName 数据源名称
     * @return 每个 Map 含 tableName-表名, remarks-表注释
     */
    public List<Map<String, Object>> getTableList(String dsName) {
        try {
            List<Map<String, Object>> rawTables = dynamicJdbcTemplate.getTableList(dsName);
            List<Map<String, Object>> tables = new ArrayList<>(rawTables.size());
            for (Map<String, Object> raw : rawTables) {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("tableName", raw.get("TABLE_NAME"));
                table.put("remarks", raw.get("TABLE_COMMENT"));
                tables.add(table);
            }
            return tables;
        } catch (Exception e) {
            throw new CommonException("518005", "读取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定表的列信息
     *
     * @param dsName    数据源名称
     * @param tableName 表名
     * @return 每个 Map 含 columnName, typeName, columnSize, decimalDigits, nullable, defaultValue, remarks, isPrimaryKey
     */
    public List<Map<String, Object>> getTableColumns(String dsName, String tableName) {
        try {
            List<Map<String, Object>> rawColumns = dynamicJdbcTemplate.getTableColumns(dsName, tableName);
            List<Map<String, Object>> columns = new ArrayList<>(rawColumns.size());
            for (Map<String, Object> raw : rawColumns) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("columnName", raw.get("COLUMN_NAME"));
                col.put("typeName", raw.get("DATA_TYPE"));
                col.put("columnSize", raw.get("CHARACTER_MAXIMUM_LENGTH"));
                col.put("decimalDigits", raw.get("NUMERIC_SCALE"));
                col.put("nullable", "YES".equals(raw.get("IS_NULLABLE")));
                col.put("defaultValue", raw.get("COLUMN_DEFAULT"));
                col.put("remarks", raw.get("COLUMN_COMMENT"));
                col.put("isPrimaryKey", "PRI".equals(raw.get("COLUMN_KEY")));
                columns.add(col);
            }
            return columns;
        } catch (Exception e) {
            throw new CommonException("518005", "读取表字段失败: " + e.getMessage());
        }
    }
}
