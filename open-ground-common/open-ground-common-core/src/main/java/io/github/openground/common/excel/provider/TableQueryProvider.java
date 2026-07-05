package io.github.openground.common.excel.provider;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.github.openground.common.excel.spi.ExcelQueryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Plus / JDBC 的表查询与插入提供者
 *
 * <p>TABLE 模式默认使用此 Provider 进行自动 SELECT 和 INSERT。</p>
 *
 * @author open-ground
 */
@Slf4j
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class TableQueryProvider implements ExcelQueryProvider {

    private final JdbcTemplate jdbcTemplate;

    public TableQueryProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        // 此方法用于 CUSTOM 模式，TABLE 模式走 queryByTable
        throw new UnsupportedOperationException("请使用 queryByTable(Map, String)");
    }

    /**
     * 根据表名查询数据
     *
     * @param params    查询条件
     * @param tableName 表名
     * @return 数据列表（Map 形式）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryByTable(Map<String, Object> params, String tableName) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(tableName);

        // 构建 WHERE 条件
        List<Object> args = new ArrayList<>();
        if (params != null && !params.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    conditions.add(entry.getKey() + " = ?");
                    args.add(entry.getValue());
                }
            }
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
        }

        log.debug("执行 SQL: {} params: {}", sql, args);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 根据自定义 SQL 查询
     *
     * @param sql    SQL 语句
     * @param params 参数
     * @return 数据列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryBySql(String sql, Map<String, Object> params) {
        List<Object> args = new ArrayList<>();
        if (params != null) {
            args.addAll(params.values());
        }
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    /**
     * 批量插入数据
     *
     * @param dataList  数据列表
     * @param tableName 目标表名
     */
    public void batchInsert(List<?> dataList, String tableName) {
        if (dataList == null || dataList.isEmpty()) return;

        // 获取表的列信息
        List<String> columns = getTableColumns(tableName);
        if (columns.isEmpty()) {
            log.warn("无法获取表 {} 的列信息，跳过批量插入", tableName);
            return;
        }

        // 构建 INSERT SQL
        String columnList = String.join(", ", columns);
        String placeholders = columns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";

        // 批量执行
        List<Object[]> batchArgs = new ArrayList<>();
        for (Object data : dataList) {
            List<Object> rowValues = new ArrayList<>();
            for (String column : columns) {
                // 尝试从对象中获取字段值（支持 Map 和 POJO）
                Object value = extractValue(data, column);
                rowValues.add(value);
            }
            batchArgs.add(rowValues.toArray());
        }

        try {
            int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
            log.info("批量插入 {} 完成：{} 行", tableName, results.length);
        } catch (Exception e) {
            log.error("批量插入 {} 失败", tableName, e);
            throw new RuntimeException("批量插入失败: " + tableName, e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 获取表的列名列表（排除自增主键）
     */
    private List<String> getTableColumns(String tableName) {
        List<String> columns = new ArrayList<>();
        try {
            DataSource ds = jdbcTemplate.getDataSource();
            if (ds == null) return columns;

            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                // 获取主键列（排除自增主键）
                Set<String> primaryKeys = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME").toLowerCase());
                    }
                }

                try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        String autoIncrement = rs.getString("IS_AUTOINCREMENT");
                        // 排除自增主键
                        if ("YES".equalsIgnoreCase(autoIncrement)) continue;
                        columns.add(colName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取表列信息失败: {}", e.getMessage());
        }
        return columns;
    }

    /**
     * 从对象中提取字段值
     */
    private Object extractValue(Object data, String column) {
        // 将下划线列名转为驼峰字段名
        String fieldName = StringUtils.underlineToCamel(column);

        if (data instanceof Map) {
            // 先尝试原 key，再尝试驼峰 key
            Object val = ((Map<?, ?>) data).get(column);
            if (val == null) {
                val = ((Map<?, ?>) data).get(fieldName);
            }
            if (val == null) {
                val = ((Map<?, ?>) data).get(column.toLowerCase());
            }
            return val;
        }

        // POJO 对象
        try {
            Field field = ReflectUtil.getField(data.getClass(), fieldName);
            if (field != null) {
                return ReflectUtil.getFieldValue(data, fieldName);
            }
            // 尝试原始列名
            field = ReflectUtil.getField(data.getClass(), column);
            if (field != null) {
                return ReflectUtil.getFieldValue(data, column);
            }
        } catch (Exception e) {
            log.trace("提取字段值失败: {}.{}", data.getClass().getSimpleName(), fieldName);
        }
        return null;
    }
}
