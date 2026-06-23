package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.insert.Insert;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 解析器（基于 JSqlParser）
 *
 * <p>使用成熟的 JSqlParser 库解析 CREATE TABLE 和 INSERT INTO 语句，
 * 替代正则表达式方案，准确率接近 100%。
 * 内部实体类保持向后兼容，不影响其他组件。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class SimpleSqlParser {

    /**
     * 解析 SQL 脚本，提取表结构和数据信息
     *
     * @param sqlContent SQL 脚本内容
     * @return 解析结果
     */
    public ParseResult parseSqlScript(String sqlContent) {
        ParseResult result = new ParseResult();

        if (sqlContent == null || sqlContent.trim().isEmpty()) {
            return result;
        }

        // 按分号分割 SQL 语句
        String[] statements = splitSqlStatements(sqlContent);

        for (String sql : statements) {
            sql = sql.trim();
            if (sql.isEmpty() || isCommentOnly(sql)) {
                continue;
            }

            try {
                parseAndProcess(sql, result);
            } catch (Exception e) {
                String preview = sql.substring(0, Math.min(80, sql.length()));
                log.warn("Failed to parse SQL: {} ... -> {}", preview, e.getMessage());
                result.addParseError(preview, e.getMessage());
            }
        }

        return result;
    }

    /**
     * 用 JSqlParser 解析单条 SQL 并归类
     * <p>对 MySQL 特有语法做兼容处理（如 UNIQUE INDEX/KEY），
     * 先尝试原生解析，失败时预处理后重试。
     */
    private void parseAndProcess(String sql, ParseResult result) throws JSQLParserException {
        Statement stmt;
        String preprocessed = null;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            // JSqlParser 4.7 不支持 MySQL 的 UNIQUE INDEX/KEY 表级约束语法
            // 替换为 INDEX 后重试，再通过正则回补 UNIQUE 标记
            if (sql.toUpperCase().trim().startsWith("CREATE TABLE")) {
                preprocessed = preprocessCreateTable(sql);
                if (!preprocessed.equals(sql)) {
                    stmt = CCJSqlParserUtil.parse(preprocessed);
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }

        if (stmt instanceof CreateTable) {
            CreateTable ct = (CreateTable) stmt;
            TableDefinition tableDef = convertCreateTable(ct);
            if (tableDef != null) {
                // 回补 UNIQUE 标记（预处理时被替换掉的）
                if (preprocessed != null) {
                    patchUniqueIndexes(sql, tableDef);
                }
                result.addTableDefinition(tableDef);
            }
        } else if (stmt instanceof Insert) {
            Insert insert = (Insert) stmt;
            InsertStatement insertStmt = convertInsert(insert);
            if (insertStmt != null) {
                result.addInsertStatement(insertStmt);
            }
        }
        // ALTER TABLE 等复杂语句直接跳过，由数据库元数据负责
    }

    /**
     * 预处理 CREATE TABLE SQL，将 JSqlParser 4.7 不支持的语法替换为支持的格式
     * <p>替换规则：
     * <ul>
     *   <li>{@code UNIQUE INDEX name (cols)} → {@code INDEX name (cols)}</li>
     *   <li>{@code UNIQUE KEY name (cols)}   → {@code INDEX name (cols)}</li>
     * </ul>
     */
    private String preprocessCreateTable(String sql) {
        return sql.replaceAll("(?i)\\bUNIQUE\\s+(INDEX|KEY)\\b", "INDEX");
    }

    /**
     * 回补 UNIQUE 标记：用正则扫描原始 SQL 中的 UNIQUE INDEX/KEY 定义，
     * 将对应的 ScriptIndexInfo 标记为 unique=true
     */
    private void patchUniqueIndexes(String originalSql, TableDefinition tableDef) {
        // 匹配: UNIQUE [INDEX|KEY] [`"]?name[`"]? (col1, col2, ...)
        // 提取索引名
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)UNIQUE\\s+(?:INDEX|KEY)\\s+[`\"']?([^\\s`\"'(),]+)",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(originalSql);
        while (matcher.find()) {
            String indexName = unquote(matcher.group(1));
            if (indexName == null || indexName.isEmpty()) continue;
            for (ScriptIndexInfo idx : tableDef.getIndexList()) {
                if (indexName.equalsIgnoreCase(idx.getIndexName())) {
                    idx.setUnique(true);
                    break;
                }
            }
        }
    }

    /**
     * 将 JSqlParser CreateTable 转换为内部 TableDefinition
     */
    private TableDefinition convertCreateTable(CreateTable ct) {
        Table table = ct.getTable();
        if (table == null) return null;

        TableDefinition tableDef = new TableDefinition();
        tableDef.setTableName(unquote(table.getName()));

        List<ColumnDefinition> cols = ct.getColumnDefinitions();
        if (cols != null) {
            for (ColumnDefinition col : cols) {
                TableColumnDefinition tcd = new TableColumnDefinition();

                // 列名
                tcd.setName(unquote(col.getColumnName()));

                // 类型 + 长度
                ColDataType dataType = col.getColDataType();
                if (dataType != null) {
                    tcd.setType(dataType.getDataType());

                    // 拼接长度/精度参数：如 VARCHAR(255) → "255"；DECIMAL(10,2) → "10,2"
                    List<String> args = dataType.getArgumentsStringList();
                    if (args != null && !args.isEmpty()) {
                        tcd.setLength(String.join(",", args));
                    }
                }

                // 提取属性串（NOT NULL, DEFAULT xxx, COMMENT 'xxx' 等）
                List<String> specs = col.getColumnSpecs();
                if (specs != null && !specs.isEmpty()) {
                    tcd.setAttributes(String.join(" ", specs));
                }

                tableDef.addColumn(tcd);
            }
        }

        // 提取表选项（ENGINE、COMMENT、CHARSET 等）
        List<String> options = ct.getTableOptionsStrings();
        if (options != null && !options.isEmpty()) {
            tableDef.setTableOptions(String.join(" ", options));
        }

        // 提取主键和索引定义
        List<Index> indexes = ct.getIndexes();
        if (indexes != null) {
            for (Index idx : indexes) {
                String type = idx.getType() != null ? idx.getType().toUpperCase() : "";
                List<String> colNames = idx.getColumnsNames();
                if (colNames == null || colNames.isEmpty()) continue;

                if ("PRIMARY KEY".equals(type)) {
                    for (String col : colNames) {
                        tableDef.getPrimaryKeyColumns().add(unquote(col));
                    }
                } else {
                    ScriptIndexInfo idxInfo = new ScriptIndexInfo();
                    if (idx.getName() != null) {
                        idxInfo.setIndexName(unquote(idx.getName()));
                    } else {
                        idxInfo.setIndexName("");
                    }
                    for (String col : colNames) {
                        idxInfo.getColumnNames().add(unquote(col));
                    }
                    idxInfo.setUnique(type.contains("UNIQUE"));
                    tableDef.getIndexList().add(idxInfo);
                }
            }
        }

        return tableDef;
    }

    /**
     * 将 JSqlParser Insert 转换为内部 InsertStatement
     */
    private InsertStatement convertInsert(Insert insert) {
        Table table = insert.getTable();
        if (table == null) return null;

        InsertStatement stmt = new InsertStatement();
        stmt.setTableName(unquote(table.getName()));

        // 列名列表 (4.7 中 getColumns() 返回 ExpressionList<Column>)
        net.sf.jsqlparser.expression.operators.relational.ExpressionList<?> colList = insert.getColumns();
        if (colList != null) {
            for (Object item : colList) {
                stmt.addColumn(unquote(item.toString()));
            }
        }

        // 值列表（取第一条 VALUES）
        net.sf.jsqlparser.statement.select.Values values = insert.getValues();
        if (values != null) {
            net.sf.jsqlparser.expression.operators.relational.ExpressionList<?> exprList = values.getExpressions();
            if (exprList != null) {
                List<String> valueStrs = new ArrayList<>();
                for (Object item : exprList) {
                    // JSqlParser 4.7 将所有值表达式包裹在 Parenthesis 中，需解包获取真实表达式
                    Expression expr = item instanceof Parenthesis
                            ? ((Parenthesis) item).getExpression()
                            : (Expression) item;

                    if (expr instanceof StringValue) {
                        valueStrs.add("'" + ((StringValue) expr).getValue() + "'");
                    } else {
                        valueStrs.add(expr.toString());
                    }
                }
                stmt.setValues(String.join(", ", valueStrs));
            }
        }

        return stmt;
    }

    /**
     * 判断是否纯粹是注释行
     */
    private boolean isCommentOnly(String sql) {
        String trimmed = sql.trim();
        return trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    /**
     * 按分号分割 SQL 语句（基础版，JSqlParser 可处理复杂换行）
     */
    private String[] splitSqlStatements(String sqlContent) {
        // 归一化换行符
        String normalized = sqlContent.replace("\r\n", "\n").replace("\r", "\n");
        // 直接用分号拆分，JSqlParser 内部处理换行和空格
        List<String> statements = new ArrayList<>();
        for (String part : normalized.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements.toArray(new String[0]);
    }

    // ========== 内部实体类（保持向后兼容）==========

    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if ((s.startsWith("`") && s.endsWith("`"))
                || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static class ParseResult {
        private final List<TableDefinition> tableDefinitions = new ArrayList<>();
        private final List<InsertStatement> insertStatements = new ArrayList<>();
        private final List<ParseError> parseErrors = new ArrayList<>();

        public void addTableDefinition(TableDefinition tableDef) {
            tableDefinitions.add(tableDef);
        }

        public void addInsertStatement(InsertStatement insertStmt) {
            insertStatements.add(insertStmt);
        }

        public void addParseError(String sqlPreview, String errorMessage) {
            parseErrors.add(new ParseError(sqlPreview, errorMessage));
        }

        public List<TableDefinition> getTableDefinitions() { return tableDefinitions; }
        public List<InsertStatement> getInsertStatements() { return insertStatements; }
        public List<ParseError> getParseErrors() { return parseErrors; }
        public boolean hasParseErrors() { return !parseErrors.isEmpty(); }
    }

    /**
     * 单条 SQL 解析失败的信息记录
     */
    public static class ParseError {
        private final String sqlPreview;
        private final String errorMessage;

        public ParseError(String sqlPreview, String errorMessage) {
            this.sqlPreview = sqlPreview;
            this.errorMessage = errorMessage;
        }

        public String getSqlPreview() { return sqlPreview; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class TableDefinition {
        private String tableName;
        private String tableOptions;
        private final List<TableColumnDefinition> columns = new ArrayList<>();
        private final List<String> primaryKeyColumns = new ArrayList<>();
        private final List<ScriptIndexInfo> indexList = new ArrayList<>();

        public void addColumn(TableColumnDefinition column) {
            columns.add(column);
        }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableOptions() { return tableOptions; }
        public void setTableOptions(String tableOptions) { this.tableOptions = tableOptions; }
        public List<TableColumnDefinition> getColumns() { return columns; }
        public List<String> getPrimaryKeyColumns() { return primaryKeyColumns; }
        public List<ScriptIndexInfo> getIndexList() { return indexList; }
    }

    public static class TableColumnDefinition {
        private String name;
        private String type;
        private String length;
        private String attributes;
        private String comment;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLength() { return length; }
        public void setLength(String length) { this.length = length; }
        public String getAttributes() { return attributes; }
        public void setAttributes(String attributes) { this.attributes = attributes; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class InsertStatement {
        private String tableName;
        private final List<String> columns = new ArrayList<>();
        private String values;

        public void addColumn(String column) {
            columns.add(column);
        }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getColumns() { return columns; }
        public String getValues() { return values; }
        public void setValues(String values) { this.values = values; }
    }

    /**
     * 脚本中定义的索引信息
     */
    public static class ScriptIndexInfo {
        private String indexName;
        private final List<String> columnNames = new ArrayList<>();
        private boolean unique;

        public String getIndexName() { return indexName; }
        public void setIndexName(String indexName) { this.indexName = indexName; }
        public List<String> getColumnNames() { return columnNames; }
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
    }
}
