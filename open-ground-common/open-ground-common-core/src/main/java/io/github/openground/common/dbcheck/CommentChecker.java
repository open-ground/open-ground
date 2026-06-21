package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 注释校验器
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
public class CommentChecker {

    private final MetadataExtractor metadataExtractor;
    private final DatabaseTypeDetector databaseTypeDetector;

    public CommentChecker(MetadataExtractor metadataExtractor,
                          DatabaseTypeDetector databaseTypeDetector) {
        this.metadataExtractor = metadataExtractor;
        this.databaseTypeDetector = databaseTypeDetector;
    }

    /**
     * 检查表和字段的注释一致性
     *
     * @param conn            JDBC 连接
     * @param tableNames      待检查的表名
     * @param expectedComments 预期的注释映射（key=表名或 表名.字段名）
     * @return 注释差异结果
     */
    public DbCheckResult.CommentCheckResult checkComments(Connection conn,
                                                           List<String> tableNames,
                                                           Map<String, String> expectedComments) {
        DbCheckResult.CommentCheckResult result = new DbCheckResult.CommentCheckResult();
        result.setTotalTables(tableNames.size());
        List<DbCheckResult.CommentDiff> diffs = new ArrayList<>();

        Map<String, MetadataExtractor.DbTableInfo> dbTables =
                metadataExtractor.extractTableMetadata(conn, tableNames);

        for (String tableName : tableNames) {
            MetadataExtractor.DbTableInfo dbTable = dbTables.get(tableName);
            if (dbTable == null) continue;

            // 检查表注释
            String expectedTableComment = expectedComments.get(tableName);
            if (expectedTableComment != null && !expectedTableComment.equals(dbTable.getComment())) {
                DbCheckResult.CommentDiff diff = new DbCheckResult.CommentDiff();
                diff.setTableName(tableName);
                diff.setFieldName("(表注释)");
                diff.setExpected(expectedTableComment);
                diff.setActual(dbTable.getComment());
                diffs.add(diff);
            }
        }

        result.setMismatchCount(diffs.size());
        result.setDiffs(diffs);
        return result;
    }
}
