package io.github.openground.common.dbcheck.service;

import io.github.openground.common.dbcheck.model.ScriptInfo;
import io.github.openground.common.dbcheck.util.DbCheckUtils;
import io.github.openground.common.dbcheck.extractor.MetadataExtractor;
import io.github.openground.common.dbcheck.extractor.ScriptPathResolver;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import io.github.openground.common.dbcheck.extractor.SqlScriptScanner;
import io.github.openground.common.dbcheck.checker.CommentChecker;
import io.github.openground.common.dbcheck.checker.DataChecker;
import io.github.openground.common.dbcheck.checker.DbSchemaComparator;
import io.github.openground.common.dbcheck.model.DbCheckProperties;
import io.github.openground.common.dbcheck.model.DbCheckResult;
import io.github.openground.common.dbcheck.util.DatabaseTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DbCheck 主服务
 * 协调数据库检查和同步流程
 *
 * <p>支持手动触发检查和同步，不依赖启动自动执行（改为通过 DbCheckController API 调用）。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Service
public class DbCheckService {

    private final DbCheckProperties dbCheckProperties;
    private final DatabaseTypeDetector databaseTypeDetector;
    private final ScriptPathResolver scriptPathResolver;
    private final SqlScriptScanner sqlScriptScanner;
    private final SimpleSqlParser simpleSqlParser;
    private final MetadataExtractor metadataExtractor;
    private final DbSchemaComparator dbSchemaComparator;
    private final DataChecker dataChecker;
    private final SchemaSyncService schemaSyncService;
    private final DataSyncService dataSyncService;
    private final CommentChecker commentChecker;

    public DbCheckService(DbCheckProperties dbCheckProperties,
                         DatabaseTypeDetector databaseTypeDetector,
                         ScriptPathResolver scriptPathResolver,
                         SqlScriptScanner sqlScriptScanner,
                         SimpleSqlParser simpleSqlParser,
                         MetadataExtractor metadataExtractor,
                         DbSchemaComparator dbSchemaComparator,
                         DataChecker dataChecker,
                         SchemaSyncService schemaSyncService,
                         DataSyncService dataSyncService,
                         CommentChecker commentChecker) {
        this.dbCheckProperties = dbCheckProperties;
        this.databaseTypeDetector = databaseTypeDetector;
        this.scriptPathResolver = scriptPathResolver;
        this.sqlScriptScanner = sqlScriptScanner;
        this.simpleSqlParser = simpleSqlParser;
        this.metadataExtractor = metadataExtractor;
        this.dbSchemaComparator = dbSchemaComparator;
        this.dataChecker = dataChecker;
        this.schemaSyncService = schemaSyncService;
        this.dataSyncService = dataSyncService;
        this.commentChecker = commentChecker;
    }

    /**
     * 执行数据库检查（仅检查指定脚本）
     *
     * <p>与 {@link #runCheck()} 的区别：只解析 scriptKeys 指定的脚本文件，
     * 不指定（null 或空列表）时与 runCheck() 行为一致。
     *
     * @param scriptKeys 要检查的脚本唯一标识列表（scriptKey）
     * @return 结构化检查结果
     */
    public DbCheckResult runCheck(List<String> scriptKeys) {
        long start = System.currentTimeMillis();
        DbCheckResult result = new DbCheckResult();

        String dbType = resolveDbType();
        result.setDbType(dbType);

        // 扫描并过滤 SQL 脚本
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        scripts = filterScriptsByKeys(scripts, scriptKeys);
        result.setScriptCount(scripts.size());

        if (scripts.isEmpty()) {
            log.warn("No SQL scripts found after filtering, skipping check");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        // 获取数据库当前表结构
        List<MetadataExtractor.DbTableInfo> dbTables = metadataExtractor.getAllTables();
        log.info("Found {} tables in database", dbTables.size());

        // 解析 SQL 脚本
        List<SimpleSqlParser.TableDefinition> scriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> scriptInserts = new ArrayList<>();
        List<SimpleSqlParser.ParseError> allParseErrors = new ArrayList<>();

        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            scriptTables.addAll(parseResult.getTableDefinitions());
            scriptInserts.addAll(parseResult.getInsertStatements());
            allParseErrors.addAll(parseResult.getParseErrors());
        }

        // 收集 SQL 解析错误
        if (!allParseErrors.isEmpty()) {
            result.setParseErrors(convertParseErrors(allParseErrors));
        }

        // 表结构检查
        if (!scriptTables.isEmpty()) {
            result.setSchemaResult(buildSchemaResult(scriptTables, dbTables));
        }

        // 数据检查
        if (dbCheckProperties.isDataCheck() && !scriptInserts.isEmpty()) {
            try {
                result.setDataResult(buildDataResult(scriptInserts, dbTables, dbType));
            } catch (Exception e) {
                log.error("数据检查失败: {}", e.getMessage(), e);
                List<SimpleSqlParser.ParseError> errList = new ArrayList<>(allParseErrors);
                errList.add(new SimpleSqlParser.ParseError("数据检查", "数据检查异常: " + e.getMessage()));
                result.setParseErrors(convertParseErrors(errList));
            }
        }

        // 注释检查
        if (dbCheckProperties.isCommentCheck() && !scriptTables.isEmpty()) {
            result.setCommentResult(buildCommentResult(scriptTables, dbTables));
        }

        // 生成同步 SQL（不执行）
        result.setSyncSqls(buildSyncSqls(scriptTables, scriptInserts, dbTables, dbType));

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 获取当前数据库类型可用的脚本文件列表
     *
     * @return 脚本元信息列表
     */
    public List<ScriptInfo> getScriptList() {
        String dbType = resolveDbType();
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        List<ScriptInfo> list = new ArrayList<>(scripts.size());
        for (SqlScriptScanner.SqlScript script : scripts) {
            list.add(new ScriptInfo(script.getScriptKey(), script.getFileName(),
                    script.getModulePath(), script.getFileSize()));
        }
        return list;
    }

    /**
     * 根据脚本唯一标识获取可下载的脚本资源
     *
     * @param scriptKey 脚本唯一标识
     * @return 脚本资源，未找到返回 null
     */
    public SqlScriptScanner.SqlScript getScriptByKey(String scriptKey) {
        if (scriptKey == null || scriptKey.trim().isEmpty()) {
            return null;
        }
        String dbType = resolveDbType();
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        String key = scriptKey.trim();
        for (SqlScriptScanner.SqlScript script : scripts) {
            if (key.equals(script.getScriptKey())) {
                return script;
            }
        }
        return null;
    }

    /**
     * 根据 scriptKeys 过滤脚本列表
     */
    private List<SqlScriptScanner.SqlScript> filterScriptsByKeys(
            List<SqlScriptScanner.SqlScript> scripts, List<String> scriptKeys) {
        if (scriptKeys == null || scriptKeys.isEmpty()) {
            return scripts;
        }
        // 构建快速查找集合
        Set<String> keySet = new HashSet<>();
        for (String key : scriptKeys) {
            if (key != null) {
                keySet.add(key.trim());
            }
        }
        if (keySet.isEmpty()) {
            return scripts;
        }
        List<SqlScriptScanner.SqlScript> filtered = new ArrayList<>();
        for (SqlScriptScanner.SqlScript script : scripts) {
            if (keySet.contains(script.getScriptKey())) {
                filtered.add(script);
            }
        }
        return filtered;
    }

    /**
     * 生成同步 SQL 列表
     */
    private List<String> buildSyncSqls(List<SimpleSqlParser.TableDefinition> scriptTables,
                                       List<SimpleSqlParser.InsertStatement> scriptInserts,
                                       List<MetadataExtractor.DbTableInfo> dbTables,
                                       String dbType) {
        List<String> allSyncSqls = new ArrayList<>();

        // 构建表名→中文注释映射（从数据库元数据中提取）
        Map<String, String> tableComments = new HashMap<>();
        for (MetadataExtractor.DbTableInfo t : dbTables) {
            if (t.getComment() != null && !t.getComment().isEmpty()) {
                tableComments.put(t.getTableName().toLowerCase(), t.getComment());
            }
        }

        if (!scriptTables.isEmpty()) {
            DbSchemaComparator.SchemaDiff diff = dbSchemaComparator.compareTables(scriptTables, dbTables);
            if (!diff.isEmpty()) {
                SchemaSyncService.SyncResult sr = schemaSyncService.syncSchema(diff, dbType, tableComments);
                allSyncSqls.addAll(sr.getSyncSqls());
            }
        }

        if (dbCheckProperties.isDataCheck() && !scriptInserts.isEmpty()) {
            DataSyncService.DataSyncResult dr = dataSyncService.syncData(
                    scriptInserts, dbType, false);
            allSyncSqls.addAll(dr.getSyncSqls());
        }

        // 补充注释同步 SQL
        if (dbCheckProperties.isCommentCheck()) {
            allSyncSqls.addAll(buildCommentSyncSqls(scriptTables, dbTables, dbType));
        }

        return allSyncSqls;
    }

    /**
     * 生成同步 SQL 列表（含多余字段 DROP COLUMN）
     *
     * <p>与 buildSyncSqls 不同，此方法对数据库中多余且脚本中不存在的字段，
     * 生成 DROP COLUMN 语句强制删除，以脚本为准。
     * 仅用于 syncSelectedTables 一体化同步操作。
     */
    private List<String> buildSyncSqlsWithDrop(List<SimpleSqlParser.TableDefinition> scriptTables,
                                                List<SimpleSqlParser.InsertStatement> scriptInserts,
                                                List<MetadataExtractor.DbTableInfo> dbTables,
                                                String dbType) {
        List<String> allSyncSqls = new ArrayList<>();

        Map<String, String> tableComments = new HashMap<>();
        for (MetadataExtractor.DbTableInfo t : dbTables) {
            if (t.getComment() != null && !t.getComment().isEmpty()) {
                tableComments.put(t.getTableName().toLowerCase(), t.getComment());
            }
        }

        if (!scriptTables.isEmpty()) {
            DbSchemaComparator.SchemaDiff diff = dbSchemaComparator.compareTables(scriptTables, dbTables);
            if (!diff.isEmpty()) {
                SchemaSyncService.SyncResult sr = schemaSyncService.syncSchemaWithDrop(diff, dbType, tableComments);
                allSyncSqls.addAll(sr.getSyncSqls());
            }
        }

        if (dbCheckProperties.isDataCheck() && !scriptInserts.isEmpty()) {
            DataSyncService.DataSyncResult dr = dataSyncService.syncData(
                    scriptInserts, dbType, false);
            allSyncSqls.addAll(dr.getSyncSqls());
        }

        if (dbCheckProperties.isCommentCheck()) {
            allSyncSqls.addAll(buildCommentSyncSqls(scriptTables, dbTables, dbType));
        }

        return allSyncSqls;
    }

    /**
     * 生成注释同步 SQL（COMMENT ON）
     * <p>比较脚本列注释与数据库列注释，生成同步语句。
     * 表级注释已由 tableOptions（脚本）或 generateCreateTableFromDbMeta（数据库）处理。
     *
     * @param scriptTables 脚本解析的表定义
     * @param dbTables     数据库元数据
     * @param dbType       数据库类型
     * @return COMMENT ON SQL 列表
     */
    private List<String> buildCommentSyncSqls(List<SimpleSqlParser.TableDefinition> scriptTables,
                                               List<MetadataExtractor.DbTableInfo> dbTables,
                                               String dbType) {
        List<String> sqls = new ArrayList<>();
        if (scriptTables.isEmpty()) return sqls;

        // 构建数据库表名→表信息的映射
        Map<String, MetadataExtractor.DbTableInfo> dbTableMap = new HashMap<>();
        for (MetadataExtractor.DbTableInfo t : dbTables) {
            dbTableMap.put(t.getTableName().toLowerCase(), t);
        }

        for (SimpleSqlParser.TableDefinition scriptTable : scriptTables) {
            String tableName = scriptTable.getTableName().toLowerCase();
            MetadataExtractor.DbTableInfo dbTable = dbTableMap.get(tableName);
            if (dbTable == null) continue;

            // 构建数据库列名→列信息的映射
            Map<String, MetadataExtractor.DbColumnInfo> dbColMap = new HashMap<>();
            for (MetadataExtractor.DbColumnInfo col : dbTable.getColumns()) {
                dbColMap.put(col.getName().toLowerCase(), col);
            }

            // 逐列对比注释
            for (SimpleSqlParser.TableColumnDefinition scriptCol : scriptTable.getColumns()) {
                String colName = scriptCol.getName().toLowerCase();
                String scriptComment = extractCommentFromAttributes(scriptCol.getAttributes());
                MetadataExtractor.DbColumnInfo dbCol = dbColMap.get(colName);
                String dbComment = (dbCol != null) ? dbCol.getComment() : null;

                // 脚本有注释，数据库无注释或不一致 → 生成 COMMENT ON
                if (scriptComment != null && !scriptComment.isEmpty()
                        && !scriptComment.equals(dbComment != null ? dbComment : "")) {
                    String quotedTable = DbCheckUtils.quoteId(scriptTable.getTableName(), dbType);
                    String quotedCol = DbCheckUtils.quoteId(scriptCol.getName(), dbType);

                    if (DbCheckUtils.isMysql(dbType)) {
                        // MySQL: ALTER TABLE t MODIFY COLUMN col type COMMENT 'xxx'
                        // 需要完整字段定义，较复杂；简化：仅当能获取到类型时生成
                        String colType = DbCheckUtils.mapColumnType(scriptCol.getType(), dbType);
                        if (scriptCol.getLength() != null && !scriptCol.getLength().isEmpty()) {
                            colType += "(" + scriptCol.getLength() + ")";
                        }
                        sqls.add("ALTER TABLE " + quotedTable + " MODIFY COLUMN " + quotedCol
                                + " " + colType + " COMMENT '" + escapeComment(scriptComment) + "'");
                    } else {
                        // Oracle/DM/PostgreSQL: COMMENT ON COLUMN t.col IS 'xxx'
                        sqls.add("COMMENT ON COLUMN " + quotedTable + "." + quotedCol
                                + " IS '" + escapeComment(scriptComment) + "'");
                    }
                }
            }
        }

        return sqls;
    }

    /**
     * 从字段属性字符串中提取 COMMENT 'xxx' 的值
     */
    private String extractCommentFromAttributes(String attributes) {
        if (attributes == null || attributes.isEmpty()) return null;
        // 查找 COMMENT 'xxx' 或 COMMENT="xxx"
        String upper = attributes.toUpperCase();
        int idx = upper.indexOf("COMMENT");
        if (idx < 0) return null;
        String after = attributes.substring(idx + 7).trim();
        if (after.startsWith("=")) after = after.substring(1).trim();
        if (after.startsWith("'")) {
            int end = after.indexOf("'", 1);
            if (end > 0) return after.substring(1, end);
        }
        if (after.startsWith("\"")) {
            int end = after.indexOf("\"", 1);
            if (end > 0) return after.substring(1, end);
        }
        return null;
    }

    /**
     * 转义注释中的单引号
     */
    private String escapeComment(String comment) {
        return comment != null ? comment.replace("'", "''") : "";
    }

    /**
     * 执行数据库检查并可选执行同步
     *
     * @param apply true=执行同步；false=仅生成 SQL
     * @return 结构化检查结果（含同步 SQL）
     */
    public DbCheckResult runCheckWithSync(boolean apply) {
        return runCheckWithSync(apply, null);
    }

    /**
     * 执行数据库检查并可选执行同步（支持指定脚本）
     *
     * @param apply      true=执行同步；false=仅生成 SQL
     * @param scriptKeys 要检查的脚本唯一标识列表（null=全部）
     * @return 结构化检查结果（含同步 SQL）
     */
    public DbCheckResult runCheckWithSync(boolean apply, List<String> scriptKeys) {
        DbCheckResult result = runCheck(scriptKeys);
        if (apply && !result.getSyncSqls().isEmpty()) {
            // 分开执行 schema 和 data 同步 SQL（前段是 schema，后段是 data）
            List<String> schemaSqls = new ArrayList<>();
            List<String> dataSqls = new ArrayList<>();
            boolean inData = false;
            for (String sql : result.getSyncSqls()) {
                if (sql.toUpperCase().startsWith("INSERT")) {
                    inData = true;
                }
                if (inData) {
                    dataSqls.add(sql);
                } else {
                    schemaSqls.add(sql);
                }
            }
            if (!schemaSqls.isEmpty()) {
                schemaSyncService.executeSyncSqls(schemaSqls);
            }
            if (!dataSqls.isEmpty()) {
                dataSyncService.executeSyncSqls(dataSqls);
            }
            result.setExecuted(true);
        }
        return result;
    }

    /**
     * 使用外部连接执行数据库检查（仅检查，不生成同步SQL）
     *
     * <p>适用于 DMP 外部数据源等场景：表结构/数据/注释检查与内置库逻辑一致，
     * 但元数据和数据查询走外部连接，不生成同步 SQL。
     *
     * @param conn   JDBC 连接（调用方负责关闭）
     * @param dbType 数据库类型（mysql/oracle/postgresql 等）
     * @return 结构化检查结果
     */
    public DbCheckResult runCheckWithConnection(Connection conn, String dbType) {
        return runCheckWithConnection(conn, dbType, null);
    }

    /**
     * 使用外部连接执行数据库检查（仅检查，指定脚本）
     *
     * @param conn       JDBC 连接（调用方负责关闭）
     * @param dbType     数据库类型
     * @param scriptKeys 要检查的脚本唯一标识列表（null=全部）
     * @return 结构化检查结果
     */
    public DbCheckResult runCheckWithConnection(Connection conn, String dbType, List<String> scriptKeys) {
        long start = System.currentTimeMillis();
        DbCheckResult result = new DbCheckResult();
        result.setDbType(dbType);

        // 扫描并过滤 SQL 脚本
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        scripts = filterScriptsByKeys(scripts, scriptKeys);
        result.setScriptCount(scripts.size());

        if (scripts.isEmpty()) {
            log.warn("No SQL scripts found, skipping check");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        // ★ 使用外部连接获取表结构
        List<MetadataExtractor.DbTableInfo> dbTables = metadataExtractor.getAllTables(conn);
        log.info("Found {} tables in external database", dbTables.size());

        // 解析 SQL 脚本
        List<SimpleSqlParser.TableDefinition> scriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> scriptInserts = new ArrayList<>();
        List<SimpleSqlParser.ParseError> allParseErrors = new ArrayList<>();

        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            scriptTables.addAll(parseResult.getTableDefinitions());
            scriptInserts.addAll(parseResult.getInsertStatements());
            allParseErrors.addAll(parseResult.getParseErrors());
        }

        // 收集 SQL 解析错误
        if (!allParseErrors.isEmpty()) {
            result.setParseErrors(convertParseErrors(allParseErrors));
        }

        // 表结构检查
        if (!scriptTables.isEmpty()) {
            result.setSchemaResult(buildSchemaResult(scriptTables, dbTables));
        }

        // 数据检查（★ 使用外部连接）
        if (dbCheckProperties.isDataCheck() && !scriptInserts.isEmpty()) {
            try {
                result.setDataResult(buildDataResultWithConn(scriptInserts, dbTables, dbType, conn));
            } catch (Exception e) {
                log.error("数据检查失败: {}", e.getMessage(), e);
                // 数据检查失败时，将错误信息加入 parseErrors，保留已收集的解析错误
                List<SimpleSqlParser.ParseError> errList = new ArrayList<>(allParseErrors);
                errList.add(new SimpleSqlParser.ParseError("数据检查", "数据检查异常: " + e.getMessage()));
                result.setParseErrors(convertParseErrors(errList));
            }
        }

        // 注释检查
        if (dbCheckProperties.isCommentCheck() && !scriptTables.isEmpty()) {
            result.setCommentResult(buildCommentResult(scriptTables, dbTables));
        }

        // 生成同步 SQL（使用外部连接获取的 dbTables）
        result.setSyncSqls(buildSyncSqls(scriptTables, scriptInserts, dbTables, dbType));
        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    public String resolveDbType() {
        String dbType = dbCheckProperties.getDatabaseType();
        if (dbType == null || dbType.isEmpty()) {
            dbType = databaseTypeDetector.detectDatabaseType();
        }
        log.info("Detected database type: {}", dbType);
        return dbType;
    }

    /**
     * 生成指定表名的同步 SQL（不执行）
     *
     * <p>用户在前端勾选有差异的表名后调用，仅生成被勾选表的 DDL 同步语句。
     *
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名列表（不区分大小写）
     * @return 同步 SQL 列表
     */
    public List<String> generateSqlsForSelected(String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        // 扫描 SQL 脚本
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        if (scripts.isEmpty()) return Collections.emptyList();

        // 解析脚本
        List<SimpleSqlParser.TableDefinition> allScriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> allScriptInserts = new ArrayList<>();
        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            allScriptTables.addAll(parseResult.getTableDefinitions());
            allScriptInserts.addAll(parseResult.getInsertStatements());
        }

        // 获取数据库元数据
        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables();

        // ★ 按勾选的表名过滤
        List<SimpleSqlParser.TableDefinition> filteredScriptTables = new ArrayList<>();
        for (SimpleSqlParser.TableDefinition t : allScriptTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredScriptTables.add(t);
            }
        }

        List<MetadataExtractor.DbTableInfo> filteredDbTables = new ArrayList<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredDbTables.add(t);
            }
        }

        List<SimpleSqlParser.InsertStatement> filteredInserts = new ArrayList<>();
        for (SimpleSqlParser.InsertStatement stmt : allScriptInserts) {
            if (selected.contains(stmt.getTableName().toLowerCase())) {
                filteredInserts.add(stmt);
            }
        }

        // 生成仅针对选中表的同步 SQL
        List<String> sqls = buildSyncSqls(filteredScriptTables, filteredInserts, filteredDbTables, dbType);

        // ★ 补充选中表中"只存在于数据库"的多余表 CREATE TABLE SQL
        List<MetadataExtractor.DbTableInfo> extraTables = new ArrayList<>();
        Set<String> scriptTableNames = new HashSet<>();
        for (SimpleSqlParser.TableDefinition t : filteredScriptTables) {
            scriptTableNames.add(t.getTableName().toLowerCase());
        }
        for (MetadataExtractor.DbTableInfo t : filteredDbTables) {
            if (!scriptTableNames.contains(t.getTableName().toLowerCase())) {
                extraTables.add(t);
            }
        }
        if (!extraTables.isEmpty()) {
            sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(extraTables, dbType));
        }

        return sqls;
    }

    /**
     * 为外部数据源生成指定表名的同步 SQL
     *
     * @param conn       外部 JDBC 连接
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名列表
     * @return 同步 SQL 列表
     */
    public List<String> generateSqlsForSelectedWithConn(Connection conn, String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        if (scripts.isEmpty()) return Collections.emptyList();

        List<SimpleSqlParser.TableDefinition> allScriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> allScriptInserts = new ArrayList<>();
        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            allScriptTables.addAll(parseResult.getTableDefinitions());
            allScriptInserts.addAll(parseResult.getInsertStatements());
        }

        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables(conn);

        List<SimpleSqlParser.TableDefinition> filteredScriptTables = new ArrayList<>();
        for (SimpleSqlParser.TableDefinition t : allScriptTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredScriptTables.add(t);
            }
        }

        List<MetadataExtractor.DbTableInfo> filteredDbTables = new ArrayList<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredDbTables.add(t);
            }
        }

        List<SimpleSqlParser.InsertStatement> filteredInserts = new ArrayList<>();
        for (SimpleSqlParser.InsertStatement stmt : allScriptInserts) {
            if (selected.contains(stmt.getTableName().toLowerCase())) {
                filteredInserts.add(stmt);
            }
        }

        List<String> sqls = buildSyncSqls(filteredScriptTables, filteredInserts, filteredDbTables, dbType);

        // ★ 补充选中表中"只存在于数据库"的多余表 CREATE TABLE SQL
        List<MetadataExtractor.DbTableInfo> extraTables = new ArrayList<>();
        Set<String> scriptTableNames = new HashSet<>();
        for (SimpleSqlParser.TableDefinition t : filteredScriptTables) {
            scriptTableNames.add(t.getTableName().toLowerCase());
        }
        for (MetadataExtractor.DbTableInfo t : filteredDbTables) {
            if (!scriptTableNames.contains(t.getTableName().toLowerCase())) {
                extraTables.add(t);
            }
        }
        if (!extraTables.isEmpty()) {
            sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(extraTables, dbType));
        }

        return sqls;
    }

    /**
     * 执行给定的 SQL 语句（针对内置数据源）
     *
     * @param sqls 要执行的 SQL 列表
     */
    public void executeSqls(List<String> sqls) {
        if (sqls == null || sqls.isEmpty()) return;

        List<String> schemaSqls = new ArrayList<>();
        List<String> dataSqls = new ArrayList<>();
        boolean inData = false;
        for (String sql : sqls) {
            if (sql.toUpperCase().trim().startsWith("INSERT")) {
                inData = true;
            }
            if (inData) {
                dataSqls.add(sql);
            } else {
                schemaSqls.add(sql);
            }
        }

        if (!schemaSqls.isEmpty()) {
            schemaSyncService.executeSyncSqls(schemaSqls);
        }
        if (!dataSqls.isEmpty()) {
            dataSyncService.executeSyncSqls(dataSqls);
        }
        log.info("已执行 {} 条 SQL（schema={}, data={}）", sqls.size(), schemaSqls.size(), dataSqls.size());
    }

    /**
     * 使用外部连接执行给定的 SQL 语句
     *
     * @param conn 外部 JDBC 连接
     * @param sqls 要执行的 SQL 列表
     */
    public void executeSqlsWithConn(Connection conn, List<String> sqls) {
        if (sqls == null || sqls.isEmpty()) return;

        List<String> schemaSqls = new ArrayList<>();
        List<String> dataSqls = new ArrayList<>();
        boolean inData = false;
        for (String sql : sqls) {
            if (sql.toUpperCase().trim().startsWith("INSERT")) {
                inData = true;
            }
            if (inData) {
                dataSqls.add(sql);
            } else {
                schemaSqls.add(sql);
            }
        }

        if (!schemaSqls.isEmpty()) {
            schemaSyncService.executeSyncSqls(conn, schemaSqls);
        }
        if (!dataSqls.isEmpty()) {
            dataSyncService.executeSyncSqls(conn, dataSqls);
        }
        log.info("已执行 {} 条 SQL（schema={}, data={}）", sqls.size(), schemaSqls.size(), dataSqls.size());
    }

    /**
     * 为勾选的表生成增量 SQL 并立即执行到数据库（内置数据源）
     *
     * <p>将 generateSqlsForSelected 和 executeSqls 合并为一个操作，
     * 确保在同一请求中完成，避免多次调用导致的一致性问题。
     *
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名列表
     * @return 已执行的 SQL 条数
     */
    public int syncSelectedTables(String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        // 扫描 SQL 脚本
        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        if (scripts.isEmpty()) return 0;

        // 解析脚本
        List<SimpleSqlParser.TableDefinition> allScriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> allScriptInserts = new ArrayList<>();
        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            allScriptTables.addAll(parseResult.getTableDefinitions());
            allScriptInserts.addAll(parseResult.getInsertStatements());
        }

        // 获取数据库元数据
        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables();

        // 按勾选的表名过滤
        List<SimpleSqlParser.TableDefinition> filteredScriptTables = new ArrayList<>();
        for (SimpleSqlParser.TableDefinition t : allScriptTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredScriptTables.add(t);
            }
        }

        List<MetadataExtractor.DbTableInfo> filteredDbTables = new ArrayList<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredDbTables.add(t);
            }
        }

        List<SimpleSqlParser.InsertStatement> filteredInserts = new ArrayList<>();
        for (SimpleSqlParser.InsertStatement stmt : allScriptInserts) {
            if (selected.contains(stmt.getTableName().toLowerCase())) {
                filteredInserts.add(stmt);
            }
        }

        // 使用 buildSyncSqlsWithDrop 生成含 DROP COLUMN 的同步 SQL
        List<String> sqls = buildSyncSqlsWithDrop(filteredScriptTables, filteredInserts, filteredDbTables, dbType);

        // ★ 补充选中表中"只存在于数据库"的多余表 CREATE TABLE SQL
        List<MetadataExtractor.DbTableInfo> extraTables = new ArrayList<>();
        Set<String> scriptTableNames = new HashSet<>();
        for (SimpleSqlParser.TableDefinition t : filteredScriptTables) {
            scriptTableNames.add(t.getTableName().toLowerCase());
        }
        for (MetadataExtractor.DbTableInfo t : filteredDbTables) {
            if (!scriptTableNames.contains(t.getTableName().toLowerCase())) {
                extraTables.add(t);
            }
        }
        if (!extraTables.isEmpty()) {
            sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(extraTables, dbType));
        }

        if (sqls.isEmpty()) {
            return 0;
        }
        executeSqls(sqls);
        return sqls.size();
    }

    /**
     * 为勾选的表生成增量 SQL 并立即执行到数据库（外部数据源）
     *
     * <p>使用外部连接获取元数据和执行 SQL，确保以脚本为准强制同步。
     *
     * @param conn       外部 JDBC 连接
     * @param dbType     数据库类型
     * @param tableNames 勾选的表名列表
     * @return 已执行的 SQL 条数
     */
    public int syncSelectedTablesWithConn(Connection conn, String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                dbCheckProperties.getLocations(), dbType);
        if (scripts.isEmpty()) return 0;

        List<SimpleSqlParser.TableDefinition> allScriptTables = new ArrayList<>();
        List<SimpleSqlParser.InsertStatement> allScriptInserts = new ArrayList<>();
        for (SqlScriptScanner.SqlScript script : scripts) {
            SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
            allScriptTables.addAll(parseResult.getTableDefinitions());
            allScriptInserts.addAll(parseResult.getInsertStatements());
        }

        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables(conn);

        List<SimpleSqlParser.TableDefinition> filteredScriptTables = new ArrayList<>();
        for (SimpleSqlParser.TableDefinition t : allScriptTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredScriptTables.add(t);
            }
        }

        List<MetadataExtractor.DbTableInfo> filteredDbTables = new ArrayList<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                filteredDbTables.add(t);
            }
        }

        List<SimpleSqlParser.InsertStatement> filteredInserts = new ArrayList<>();
        for (SimpleSqlParser.InsertStatement stmt : allScriptInserts) {
            if (selected.contains(stmt.getTableName().toLowerCase())) {
                filteredInserts.add(stmt);
            }
        }

        List<String> sqls = buildSyncSqlsWithDrop(filteredScriptTables, filteredInserts, filteredDbTables, dbType);

        // 补充多余表 CREATE TABLE
        List<MetadataExtractor.DbTableInfo> extraTables = new ArrayList<>();
        Set<String> scriptTableNames = new HashSet<>();
        for (SimpleSqlParser.TableDefinition t : filteredScriptTables) {
            scriptTableNames.add(t.getTableName().toLowerCase());
        }
        for (MetadataExtractor.DbTableInfo t : filteredDbTables) {
            if (!scriptTableNames.contains(t.getTableName().toLowerCase())) {
                extraTables.add(t);
            }
        }
        if (!extraTables.isEmpty()) {
            sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(extraTables, dbType));
        }

        if (sqls.isEmpty()) {
            return 0;
        }
        executeSqlsWithConn(conn, sqls);
        return sqls.size();
    }

    /**
     * 为指定表生成完整 CREATE TABLE SQL（内置数据源）
     *
     * <p>根据数据库中的实际表结构反向生成完整的 CREATE TABLE 语句，
     * 包含字段类型、非空约束、默认值、表注释和列注释。
     *
     * @param dbType     数据库类型
     * @param tableNames 表名列表
     * @return 完整 CREATE TABLE SQL 列表
     */
    public List<String> generateFullCreateSqls(String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        List<String> sqls = new ArrayList<>();

        // 从数据库获取表元数据
        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables();

        // 记录已在数据库中找到的表名
        Set<String> foundInDb = new HashSet<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(
                        Collections.singletonList(t), dbType));
                foundInDb.add(t.getTableName().toLowerCase());
            }
        }

        // 数据库中不存在的表（缺失表），从脚本定义生成 CREATE TABLE
        Set<String> missingInDb = new HashSet<>(selected);
        missingInDb.removeAll(foundInDb);
        if (!missingInDb.isEmpty()) {
            List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                    dbCheckProperties.getLocations(), dbType);
            for (SqlScriptScanner.SqlScript script : scripts) {
                SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
                for (SimpleSqlParser.TableDefinition table : parseResult.getTableDefinitions()) {
                    if (missingInDb.contains(table.getTableName().toLowerCase())) {
                        sqls.add(schemaSyncService.generateCreateTableSql(table, dbType, null));
                    }
                }
            }
        }

        return sqls;
    }

    /**
     * 为指定表生成完整 CREATE TABLE SQL（外部数据源）
     *
     * @param conn       外部 JDBC 连接
     * @param dbType     数据库类型
     * @param tableNames 表名列表
     * @return 完整 CREATE TABLE SQL 列表
     */
    public List<String> generateFullCreateSqlsWithConn(Connection conn, String dbType, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> selected = new HashSet<>();
        for (String n : tableNames) {
            selected.add(n.toLowerCase());
        }

        List<String> sqls = new ArrayList<>();

        // 从数据库获取表元数据
        List<MetadataExtractor.DbTableInfo> allDbTables = metadataExtractor.getAllTables(conn);

        Set<String> foundInDb = new HashSet<>();
        for (MetadataExtractor.DbTableInfo t : allDbTables) {
            if (selected.contains(t.getTableName().toLowerCase())) {
                sqls.addAll(schemaSyncService.generateCreateSqlsForExtraTables(
                        Collections.singletonList(t), dbType));
                foundInDb.add(t.getTableName().toLowerCase());
            }
        }

        // 数据库中不存在的表（缺失表），从脚本定义生成 CREATE TABLE
        Set<String> missingInDb = new HashSet<>(selected);
        missingInDb.removeAll(foundInDb);
        if (!missingInDb.isEmpty()) {
            List<SqlScriptScanner.SqlScript> scripts = sqlScriptScanner.scanScripts(
                    dbCheckProperties.getLocations(), dbType);
            for (SqlScriptScanner.SqlScript script : scripts) {
                SimpleSqlParser.ParseResult parseResult = simpleSqlParser.parseSqlScript(script.getContent());
                for (SimpleSqlParser.TableDefinition table : parseResult.getTableDefinitions()) {
                    if (missingInDb.contains(table.getTableName().toLowerCase())) {
                        sqls.add(schemaSyncService.generateCreateTableSql(table, dbType, null));
                    }
                }
            }
        }

        return sqls;
    }

    private DbCheckResult.SchemaCheckResult buildSchemaResult(
            List<SimpleSqlParser.TableDefinition> scriptTables,
            List<MetadataExtractor.DbTableInfo> dbTables) {
        DbSchemaComparator.SchemaDiff diff = dbSchemaComparator.compareTables(scriptTables, dbTables);
        DbCheckResult.SchemaCheckResult r = new DbCheckResult.SchemaCheckResult();

        if (diff.isEmpty()) return r;

        r.setConsistent(false);

        // 缺失表
        for (SimpleSqlParser.TableDefinition t : diff.getMissingTables()) {
            r.getMissingTables().add(t.getTableName());
        }

        // 多余表
        for (MetadataExtractor.DbTableInfo t : diff.getExtraTables()) {
            r.getExtraTables().add(t.getTableName());
        }

        // 字段差异
        for (Map.Entry<String, DbSchemaComparator.TableColumnDiff> entry : diff.getColumnDiffs().entrySet()) {
            DbCheckResult.ColumnDiffItem item = new DbCheckResult.ColumnDiffItem();
            item.setTableName(entry.getKey());
            DbSchemaComparator.TableColumnDiff tcd = entry.getValue();

            for (SimpleSqlParser.TableColumnDefinition col : tcd.getMissingColumns()) {
                item.getMissingColumns().add(col.getName() + "(" + col.getType() + ")");
            }
            for (MetadataExtractor.DbColumnInfo col : tcd.getExtraColumns()) {
                item.getExtraColumns().add(col.getName());
            }
            for (Map.Entry<String, DbSchemaComparator.ColumnTypeMismatch> tm : tcd.getTypeMismatches().entrySet()) {
                DbSchemaComparator.ColumnTypeMismatch m = tm.getValue();
                MetadataExtractor.DbColumnInfo dbCol = m.getDbColumn();
                String scriptLen = m.getScriptColumn().getLength();
                item.getTypeMismatches().add(m.getScriptColumn().getName()
                        + ": script=" + m.getScriptColumn().getType()
                        + (scriptLen != null && !scriptLen.isEmpty() ? "(" + scriptLen + ")" : "")
                        + ", db=" + (dbCol != null ? dbCol.getType() : "?"));
            }
            for (DbSchemaComparator.NullableDiff nd : tcd.getNullableDiffs()) {
                item.getNullableDiffs().add(nd.getColumnName()
                        + ": script=" + (nd.isScriptNotNull() ? "NOT NULL" : "NULLABLE")
                        + ", db=" + (nd.isDbNotNull() ? "NOT NULL" : "NULLABLE"));
            }
            r.getColumnDiffs().add(item);
        }

        // 主键差异
        for (Map.Entry<String, DbSchemaComparator.PkDiff> entry : diff.getPkDiffs().entrySet()) {
            String tableName = entry.getKey();
            DbSchemaComparator.PkDiff pkDiff = entry.getValue();
            List<String> lines = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("脚本主键: [");
            List<String> scriptPk = pkDiff.getScriptPk();
            for (int i = 0; i < scriptPk.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(scriptPk.get(i));
            }
            sb.append("]  数据库主键: [");
            List<String> dbPk = pkDiff.getDbPk();
            for (int i = 0; i < dbPk.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(dbPk.get(i));
            }
            sb.append("]");
            lines.add(sb.toString());
            r.getPkDiffs().put(tableName, lines);
        }

        // 索引差异
        for (Map.Entry<String, List<DbSchemaComparator.IndexDiff>> entry : diff.getIndexDiffs().entrySet()) {
            String tableName = entry.getKey();
            List<String> lines = new ArrayList<>();
            for (DbSchemaComparator.IndexDiff idxDiff : entry.getValue()) {
                StringBuilder sb = new StringBuilder();
                if ("missing".equals(idxDiff.getType())) {
                    sb.append("缺失索引: ");
                    if (idxDiff.getScriptIndexName() != null && !idxDiff.getScriptIndexName().isEmpty()) {
                        sb.append(idxDiff.getScriptIndexName());
                    } else {
                        sb.append("(未命名)");
                    }
                    sb.append("(");
                    List<String> cols = idxDiff.getScriptColumns();
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(cols.get(i));
                    }
                    sb.append(")");
                    if (idxDiff.isScriptUnique()) {
                        sb.append(" [UNIQUE]");
                    }
                } else if ("unique_mismatch".equals(idxDiff.getType())) {
                    sb.append("索引唯一性不一致: ");
                    sb.append(idxDiff.getScriptIndexName()).append(" 脚本=")
                            .append(idxDiff.isScriptUnique() ? "UNIQUE" : "非UNIQUE")
                            .append(", 数据库=")
                            .append(idxDiff.isDbUnique() ? "UNIQUE" : "非UNIQUE");
                }
                lines.add(sb.toString());
            }
            r.getIndexDiffs().put(tableName, lines);
        }

        return r;
    }

    private DbCheckResult.DataCheckResult buildDataResult(
            List<SimpleSqlParser.InsertStatement> insertStatements,
            List<MetadataExtractor.DbTableInfo> dbTables,
            String dbType) {
        // 按表名分组 INSERT 语句
        Map<String, List<SimpleSqlParser.InsertStatement>> grouped = new LinkedHashMap<>();
        for (SimpleSqlParser.InsertStatement stmt : insertStatements) {
            grouped.computeIfAbsent(stmt.getTableName().toLowerCase(), k -> new ArrayList<>()).add(stmt);
        }

        // 按表名匹配 DB 元数据
        Map<String, MetadataExtractor.DbTableInfo> tableMap = new HashMap<>();
        for (MetadataExtractor.DbTableInfo t : dbTables) {
            tableMap.put(t.getTableName().toLowerCase(), t);
        }

        DbCheckResult.DataCheckResult r = new DbCheckResult.DataCheckResult();
        int totalMismatch = 0;
        int totalExtraRows = 0;

        for (Map.Entry<String, List<SimpleSqlParser.InsertStatement>> entry : grouped.entrySet()) {
            String tableKey = entry.getKey();
            List<SimpleSqlParser.InsertStatement> stmts = entry.getValue();
            MetadataExtractor.DbTableInfo tableInfo = tableMap.get(tableKey);
            if (tableInfo == null) continue;

            DataChecker.DataCheckResult checkResult = dataChecker.checkDataConsistency(stmts, tableInfo, dbType);
            if (checkResult.isEmpty()) continue;

            int m = checkResult.getMismatchCount();
            int e = checkResult.getExtraRowCount();

            if (m > 0) {
                totalMismatch += m;
                for (DataChecker.DataRecordDiff d : checkResult.getDiffs()) {
                    // 结构化详情
                    DbCheckResult.DataDiffItem item = new DbCheckResult.DataDiffItem();
                    item.setTable(tableKey);
                    item.setMissing(d.isMissing());
                    List<Map<String, String>> fields = new ArrayList<>();
                    for (DataChecker.FieldDiff fd : d.getFields()) {
                        Map<String, String> fm = new HashMap<>();
                        fm.put("column", fd.getColumn());
                        fm.put("scriptVal", fd.getScriptVal());
                        fm.put("dbVal", fd.getDbVal());
                        fields.add(fm);
                    }
                    item.setFields(fields);
                    r.getDiffDetails().add(item);

                    // 文本描述（兼容旧字段）
                    String desc = tableKey + ": ";
                    if (d.isMissing()) {
                        desc += "行在数据库中不存在（键=" + d.getFields().get(0).getColumn()
                                + "=" + d.getFields().get(0).getScriptVal() + "）";
                    } else {
                        desc += d.getFields().size() + " 个字段不一致";
                    }
                    r.getDiffs().add(desc);
                }
            }
            if (e > 0) {
                totalExtraRows += e;
                r.getDiffs().add(tableKey + ": 数据库多余 " + e + " 条记录");
            }
        }

        if (totalMismatch > 0 || totalExtraRows > 0) {
            r.setConsistent(false);
            r.setConflictCount(totalMismatch);
            r.setUpsertCount(totalExtraRows);
        }
        return r;
    }

    /**
     * 构建数据检查结果（使用外部连接）
     */
    private DbCheckResult.DataCheckResult buildDataResultWithConn(
            List<SimpleSqlParser.InsertStatement> insertStatements,
            List<MetadataExtractor.DbTableInfo> dbTables,
            String dbType, Connection conn) {
        // 按表名分组 INSERT 语句
        Map<String, List<SimpleSqlParser.InsertStatement>> grouped = new LinkedHashMap<>();
        for (SimpleSqlParser.InsertStatement stmt : insertStatements) {
            grouped.computeIfAbsent(stmt.getTableName().toLowerCase(), k -> new ArrayList<>()).add(stmt);
        }

        // 按表名匹配 DB 元数据
        Map<String, MetadataExtractor.DbTableInfo> tableMap = new HashMap<>();
        for (MetadataExtractor.DbTableInfo t : dbTables) {
            tableMap.put(t.getTableName().toLowerCase(), t);
        }

        DbCheckResult.DataCheckResult r = new DbCheckResult.DataCheckResult();
        int totalMismatch = 0;
        int totalExtraRows = 0;

        for (Map.Entry<String, List<SimpleSqlParser.InsertStatement>> entry : grouped.entrySet()) {
            String tableKey = entry.getKey();
            List<SimpleSqlParser.InsertStatement> stmts = entry.getValue();
            MetadataExtractor.DbTableInfo tableInfo = tableMap.get(tableKey);
            if (tableInfo == null) continue;

            // ★ 使用外部连接
            DataChecker.DataCheckResult checkResult = dataChecker.checkDataConsistency(stmts, tableInfo, dbType, conn);
            if (checkResult.isEmpty()) continue;

            int m = checkResult.getMismatchCount();
            int e = checkResult.getExtraRowCount();

            if (m > 0) {
                totalMismatch += m;
                for (DataChecker.DataRecordDiff d : checkResult.getDiffs()) {
                    DbCheckResult.DataDiffItem item = new DbCheckResult.DataDiffItem();
                    item.setTable(tableKey);
                    item.setMissing(d.isMissing());
                    List<Map<String, String>> fields = new ArrayList<>();
                    for (DataChecker.FieldDiff fd : d.getFields()) {
                        Map<String, String> fm = new HashMap<>();
                        fm.put("column", fd.getColumn());
                        fm.put("scriptVal", fd.getScriptVal());
                        fm.put("dbVal", fd.getDbVal());
                        fields.add(fm);
                    }
                    item.setFields(fields);
                    r.getDiffDetails().add(item);

                    String desc = tableKey + ": ";
                    if (d.isMissing()) {
                        desc += "行在数据库中不存在（键=" + d.getFields().get(0).getColumn()
                                + "=" + d.getFields().get(0).getScriptVal() + "）";
                    } else {
                        desc += d.getFields().size() + " 个字段不一致";
                    }
                    r.getDiffs().add(desc);
                }
            }
            if (e > 0) {
                totalExtraRows += e;
                r.getDiffs().add(tableKey + ": 数据库多余 " + e + " 条记录");
            }
        }

        if (totalMismatch > 0 || totalExtraRows > 0) {
            r.setConsistent(false);
            r.setConflictCount(totalMismatch);
            r.setUpsertCount(totalExtraRows);
        }
        return r;
    }

    private DbCheckResult.CommentCheckResult buildCommentResult(
            List<SimpleSqlParser.TableDefinition> scriptTables,
            List<MetadataExtractor.DbTableInfo> dbTables) {
        CommentChecker.CommentDiff diff = commentChecker.checkComments(scriptTables, dbTables);
        DbCheckResult.CommentCheckResult r = new DbCheckResult.CommentCheckResult();

        if (diff.isEmpty()) return r;

        r.setConsistent(false);

        for (Map.Entry<String, List<CommentChecker.CommentMismatch>> entry : diff.getMismatches().entrySet()) {
            for (CommentChecker.CommentMismatch m : entry.getValue()) {
                Map<String, String> item = new HashMap<>();
                item.put("table", entry.getKey());
                item.put("column", m.getColumnName());
                item.put("scriptComment", m.getScriptComment());
                item.put("dbComment", m.getDbComment());
                r.getMismatches().add(item);
            }
        }
        for (Map.Entry<String, List<CommentChecker.CommentInfo>> entry : diff.getMissingComments().entrySet()) {
            for (CommentChecker.CommentInfo info : entry.getValue()) {
                Map<String, String> item = new HashMap<>();
                item.put("table", entry.getKey());
                item.put("column", info.getColumnName());
                item.put("comment", info.getComment());
                r.getMissingComments().add(item);
            }
        }
        return r;
    }

    /**
     * 将 SimpleSqlParser 的解析错误列表转换为 DbCheckResult.ParseErrorInfo 列表
     *
     * @param parseErrors 解析器原始错误列表
     * @return 转换后的错误信息列表
     */
    private List<DbCheckResult.ParseErrorInfo> convertParseErrors(List<SimpleSqlParser.ParseError> parseErrors) {
        List<DbCheckResult.ParseErrorInfo> result = new ArrayList<>(parseErrors.size());
        for (SimpleSqlParser.ParseError pe : parseErrors) {
            DbCheckResult.ParseErrorInfo info = new DbCheckResult.ParseErrorInfo();
            info.setSqlPreview(pe.getSqlPreview());
            info.setErrorMessage(pe.getErrorMessage());
            result.add(info);
        }
        return result;
    }
}
