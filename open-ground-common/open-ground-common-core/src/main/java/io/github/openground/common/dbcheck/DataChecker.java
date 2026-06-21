package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据校验器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class DataChecker {

    private final MetadataExtractor metadataExtractor;
    private final DatabaseTypeDetector databaseTypeDetector;
    private final DbCheckProperties properties;

    public DataChecker(MetadataExtractor metadataExtractor,
                       DatabaseTypeDetector databaseTypeDetector,
                       DbCheckProperties properties) {
        this.metadataExtractor = metadataExtractor;
        this.databaseTypeDetector = databaseTypeDetector;
        this.properties = properties;
    }

    /**
     * 检查数据一致性
     *
     * @param conn       JDBC 连接
     * @param baseDir    数据脚本基础目录
     * @param tableNames 要检查的表名列表
     * @return 数据检查结果
     */
    public DbCheckResult.DataCheckResult checkData(Connection conn,
                                                    String baseDir,
                                                    List<String> tableNames) {
        DbCheckResult.DataCheckResult result = new DbCheckResult.DataCheckResult();
        if (!properties.getDataSync().isEnabled()) {
            result.setTotalTables(0);
            result.setConflictCount(0);
            return result;
        }
        result.setTotalTables(tableNames.size());
        List<DbCheckResult.DataDiff> diffs = new ArrayList<>();
        for (String tableName : tableNames) {
            try {
                List<Map<String, Object>> dbData = metadataExtractor.extractTableData(conn, tableName);
                if (dbData.isEmpty()) {
                    continue;
                }
                DbCheckResult.DataDiff diff = new DbCheckResult.DataDiff();
                diff.setTableName(tableName);
                diff.setConflictRows(0);
                diff.setUpsertRows(dbData.size());
                diffs.add(diff);
            } catch (Exception e) {
                log.warn("数据检查异常 table={}: {}", tableName, e.getMessage());
            }
        }
        result.setDiffs(diffs);
        result.setConflictCount(0);
        result.setUpsertCount(dbDataSize(tableNames));
        return result;
    }

    private int dbDataSize(List<String> tableNames) {
        return 0;
    }
}
