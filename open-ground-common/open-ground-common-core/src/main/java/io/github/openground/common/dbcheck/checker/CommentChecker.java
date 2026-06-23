package io.github.openground.common.dbcheck.checker;

import io.github.openground.common.dbcheck.extractor.MetadataExtractor;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字段注释检查器
 * 检查字段中文注释是否完整和一致
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class CommentChecker {

    /**
     * 检查字段注释一致性
     *
     * @param scriptTables 脚本中的表结构
     * @param dbTables 数据库中的表结构
     * @return 注释差异报告
     */
    public CommentDiff checkComments(List<SimpleSqlParser.TableDefinition> scriptTables,
                                    List<MetadataExtractor.DbTableInfo> dbTables) {
        CommentDiff diff = new CommentDiff();

        // 创建表名到表信息的映射
        Map<String, SimpleSqlParser.TableDefinition> scriptTableMap = new HashMap<>();
        Map<String, MetadataExtractor.DbTableInfo> dbTableMap = new HashMap<>();

        for (SimpleSqlParser.TableDefinition table : scriptTables) {
            scriptTableMap.put(table.getTableName().toLowerCase(), table);
        }

        for (MetadataExtractor.DbTableInfo table : dbTables) {
            dbTableMap.put(table.getTableName().toLowerCase(), table);
        }

        // 检查每个表的字段注释
        for (Map.Entry<String, SimpleSqlParser.TableDefinition> entry : scriptTableMap.entrySet()) {
            String tableName = entry.getKey();
            SimpleSqlParser.TableDefinition scriptTable = entry.getValue();

            if (dbTableMap.containsKey(tableName)) {
                MetadataExtractor.DbTableInfo dbTable = dbTableMap.get(tableName);
                checkTableComments(scriptTable, dbTable, diff);
            }
        }

        return diff;
    }

    /**
     * 检查单个表的字段注释
     */
    private void checkTableComments(SimpleSqlParser.TableDefinition scriptTable,
                                   MetadataExtractor.DbTableInfo dbTable,
                                   CommentDiff diff) {
        // 创建字段名到注释的映射
        Map<String, String> scriptColumnComments = new HashMap<>();
        Map<String, String> dbColumnComments = new HashMap<>();

        // 从脚本中提取注释（需要从字段属性中解析）
        for (SimpleSqlParser.TableColumnDefinition column : scriptTable.getColumns()) {
            String comment = extractCommentFromAttributes(column.getAttributes());
            if (comment != null && !comment.isEmpty()) {
                scriptColumnComments.put(column.getName().toLowerCase(), comment);
            }
        }

        // 从数据库元数据中提取注释
        for (MetadataExtractor.DbColumnInfo column : dbTable.getColumns()) {
            if (column.getComment() != null && !column.getComment().isEmpty()) {
                dbColumnComments.put(column.getName().toLowerCase(), column.getComment());
            }
        }

        // 对比注释
        for (Map.Entry<String, String> entry : scriptColumnComments.entrySet()) {
            String columnName = entry.getKey();
            String scriptComment = entry.getValue();

            if (dbColumnComments.containsKey(columnName)) {
                String dbComment = dbColumnComments.get(columnName);
                if (!scriptComment.equals(dbComment)) {
                    diff.addCommentMismatch(scriptTable.getTableName(), columnName, 
                            scriptComment, dbComment);
                }
            } else {
                diff.addMissingComment(scriptTable.getTableName(), columnName, scriptComment);
            }
        }

        // 检查数据库中缺少注释的字段
        for (Map.Entry<String, String> entry : dbColumnComments.entrySet()) {
            String columnName = entry.getKey();
            if (!scriptColumnComments.containsKey(columnName)) {
                diff.addExtraComment(scriptTable.getTableName(), columnName, entry.getValue());
            }
        }
    }

    /**
     * 从字段属性中提取注释
     */
    private String extractCommentFromAttributes(String attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        // 支持多种注释格式
        // MySQL: COMMENT '中文注释'
        // Oracle: COMMENT ON COLUMN table.column IS '中文注释'
        
        Pattern pattern = Pattern.compile("COMMENT\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(attributes);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * 检查注释是否为中文
     */
    public boolean isChineseComment(String comment) {
        if (comment == null || comment.isEmpty()) {
            return false;
        }
        
        // 检查是否包含中文字符
        for (char c : comment.toCharArray()) {
            if (isChineseCharacter(c)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 判断是否为中文字符
     */
    private boolean isChineseCharacter(char c) {
        // 中文字符的 Unicode 范围（基本多文种平面）
        return (c >= '\u4e00' && c <= '\u9fff') ||  // CJK 统一汉字
               (c >= '\u3400' && c <= '\u4dff') ||  // CJK 统一汉字扩展 A
               (c >= '\uf900' && c <= '\ufaff');    // CJK 兼容汉字
    }

    // ========== 内部实体类 ==========

    public static class CommentDiff {
        private Map<String, List<CommentMismatch>> mismatches = new HashMap<>();
        private Map<String, List<CommentInfo>> missingComments = new HashMap<>();
        private Map<String, List<CommentInfo>> extraComments = new HashMap<>();

        public void addCommentMismatch(String tableName, String columnName, 
                                      String scriptComment, String dbComment) {
            mismatches.computeIfAbsent(tableName, k -> new ArrayList<>())
                    .add(new CommentMismatch(columnName, scriptComment, dbComment));
        }

        public void addMissingComment(String tableName, String columnName, String comment) {
            missingComments.computeIfAbsent(tableName, k -> new ArrayList<>())
                    .add(new CommentInfo(columnName, comment));
        }

        public void addExtraComment(String tableName, String columnName, String comment) {
            extraComments.computeIfAbsent(tableName, k -> new ArrayList<>())
                    .add(new CommentInfo(columnName, comment));
        }

        public boolean isEmpty() {
            return mismatches.isEmpty() && missingComments.isEmpty() && extraComments.isEmpty();
        }

        // Getters
        public Map<String, List<CommentMismatch>> getMismatches() { return mismatches; }
        public Map<String, List<CommentInfo>> getMissingComments() { return missingComments; }
        public Map<String, List<CommentInfo>> getExtraComments() { return extraComments; }
    }

    public static class CommentMismatch {
        private String columnName;
        private String scriptComment;
        private String dbComment;

        public CommentMismatch(String columnName, String scriptComment, String dbComment) {
            this.columnName = columnName;
            this.scriptComment = scriptComment;
            this.dbComment = dbComment;
        }

        // Getters
        public String getColumnName() { return columnName; }
        public String getScriptComment() { return scriptComment; }
        public String getDbComment() { return dbComment; }
    }

    public static class CommentInfo {
        private String columnName;
        private String comment;

        public CommentInfo(String columnName, String comment) {
            this.columnName = columnName;
            this.comment = comment;
        }

        // Getters
        public String getColumnName() { return columnName; }
        public String getComment() { return comment; }
    }
}
