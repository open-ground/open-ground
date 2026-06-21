package io.github.openground.common.dbcheck;

import io.github.openground.common.dbcheck.MetadataExtractor.DbTableInfo;
import io.github.openground.common.dbcheck.SimpleSqlParser.InsertStatement;
import io.github.openground.common.dbcheck.SimpleSqlParser.TableColumnDefinition;
import io.github.openground.common.dbcheck.SimpleSqlParser.TableDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DbCheck 主服务
 * 协调数据库检查和同步流程
 *
 * <p>支持手动触发检查和同步，不依赖启动自动执行。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
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
    private final DbCheckUtils dbCheckUtils;

    public DbCheckService(DbCheckProperties dbCheckProperties,
                          DatabaseTypeDetector databaseTypeDetector,
                          ScriptPathResolver scriptPathResolver,
                          SqlScriptScanner sqlScriptScanner,
                          SimpleSqlParser simpleSqlParser,
                          MetadataExtractor metadataExtractor,
                          DbSchemaComparator dbSchemaComparator,
                          DataChecker dataChecker,
                          SchemaSyncService schemaSyncService,
                          DbCheckUtils dbCheckUtils) {
        this.dbCheckProperties = dbCheckProperties;
        this.databaseTypeDetector = databaseTypeDetector;
        this.scriptPathResolver = scriptPathResolver;
        this.sqlScriptScanner = sqlScriptScanner;
        this.simpleSqlParser = simpleSqlParser;
        this.metadataExtractor = metadataExtractor;
        this.dbSchemaComparator = dbSchemaComparator;
        this.dataChecker = dataChecker;
        this.schemaSyncService = schemaSyncService;
        this.dbCheckUtils = dbCheckUtils;
    }

    /**
     * 执行全量检查（使用主数据源）
     */
    public DbCheckResult runCheck(String dbType) {
        return runCheckInternal(null, dbType);
    }

    /**
     * 使用外部连接执行全量检查
     */
    public DbCheckResult runCheckWithConnection(Connection conn, String dbType) {
        return runCheckInternal(conn, dbType);
    }

    /**
     * 为指定表生成同步 SQL（使用主数据源）
     */
    public List<String> generateSqlsForSelected(String dbType, List<String> tableNames) {
        return generateSqlsForSelectedInternal(null, dbType, tableNames);
    }

    /**
     * 使用外部连接为指定表生成同步 SQL
     */
    public List<String> generateSqlsForSelectedWithConn(Connection conn, String dbType, List<String> tableNames) {
        return generateSqlsForSelectedInternal(conn, dbType, tableNames);
    }

    // ===== 内部实现 =====

    private DbCheckResult runCheckInternal(Connection externalConn, String dbType) {
        DbCheckResult result = new DbCheckResult();
        result.setDbType(dbType);
        result.setScriptCount(0);

        Connection conn = externalConn;
        boolean closeConn = false;
        try {
            if (conn == null) {
                conn = dbCheckUtils.createConnection(dbCheckProperties);
                closeConn = true;
            }
            if (conn == null) {
                log.warn("无法获取数据库连接，跳过检查");
                return result;
            }

            // 1. 扫描并解析脚本
            List<File> sqlFiles = sqlScriptScanner.scanSqlFiles(dbType);
            result.setScriptCount(sqlFiles.size());
            log.info("扫描到 {} 个 SQL 脚本文件 (dbType={})", sqlFiles.size(), dbType);

            List<TableDefinition> scriptTables = new ArrayList<>();
            for (File sqlFile : sqlFiles) {
                String sqlContent = sqlScriptScanner.readSqlFile(sqlFile);
                List<TableDefinition> tables = simpleSqlParser.parseCreateTables(sqlContent);
                scriptTables.addAll(tables);
            }

            // 2. 提取数据库元数据
            List<String> allTableNames = new ArrayList<>();
            for (TableDefinition def : scriptTables) {
                allTableNames.add(def.getTableName());
            }
            Map<String, DbTableInfo> dbTables = metadataExtractor.extractTableMetadata(conn, allTableNames);

            // 3. 比较表结构
            List<DbSchemaComparator.SchemaDiff> schemaDiffs =
                    dbSchemaComparator.compare(scriptTables, dbTables);
            DbCheckResult.SchemaCheckResult schemaResult = new DbCheckResult.SchemaCheckResult();
            schemaResult.setTotalTables(scriptTables.size());
            int matched = 0, mismatched = 0, onlyInScript = 0, onlyInDb = 0;
            List<DbCheckResult.TableDiff> tableDiffs = new ArrayList<>();
            for (DbSchemaComparator.SchemaDiff diff : schemaDiffs) {
                DbCheckResult.TableDiff td = new DbCheckResult.TableDiff();
                td.setTableName(diff.getTableName());
                td.setStatus(diff.getType());
                List<String> details = new ArrayList<>();
                details.add(diff.getDescription());
                td.setDetails(details);
                tableDiffs.add(td);

                switch (diff.getType()) {
                    case "ONLY_IN_SCRIPT": onlyInScript++; break;
                    case "ONLY_IN_DATABASE": onlyInDb++; break;
                    default: mismatched++; break;
                }
            }
            if (schemaDiffs.isEmpty()) {
                matched = scriptTables.size();
            }
            schemaResult.setMatchedTables(matched);
            schemaResult.setMismatchedTables(mismatched);
            schemaResult.setOnlyInScript(onlyInScript);
            schemaResult.setOnlyInDatabase(onlyInDb);
            schemaResult.setDiffs(tableDiffs);
            result.setSchemaResult(schemaResult);

            // 4. 检查数据
            if (dbCheckProperties.getDataSync().isEnabled()) {
                DbCheckResult.DataCheckResult dataResult =
                        dataChecker.checkData(conn, scriptPathResolver.resolveScriptDir(), allTableNames);
                result.setDataResult(dataResult);
            }

            // 5. 生成同步 SQL
            List<String> syncSqls = generateSyncSqls(scriptTables, dbTables, schemaDiffs);
            result.setSyncSqls(syncSqls);

            log.info("DbCheck 完成: totalTables={}, matched={}, mismatched={}, syncSqls={}",
                    scriptTables.size(), matched, mismatched, syncSqls.size());
        } catch (Exception e) {
            log.error("DbCheck 执行失败", e);
        } finally {
            if (closeConn && conn != null) {
                dbCheckUtils.closeQuietly(conn);
            }
        }

        return result;
    }

    private List<String> generateSqlsForSelectedInternal(Connection externalConn,
                                                          String dbType,
                                                          List<String> tableNames) {
        List<String> syncSqls = new ArrayList<>();

        Connection conn = externalConn;
        boolean closeConn = false;
        try {
            if (conn == null) {
                conn = dbCheckUtils.createConnection(dbCheckProperties);
                closeConn = true;
            }
            if (conn == null) return syncSqls;

            // 扫描并筛选指定表的脚本
            List<File> sqlFiles = sqlScriptScanner.scanSqlFiles(dbType);
            List<TableDefinition> scriptTables = new ArrayList<>();
            for (File sqlFile : sqlFiles) {
                String sqlContent = sqlScriptScanner.readSqlFile(sqlFile);
                List<TableDefinition> tables = simpleSqlParser.parseCreateTables(sqlContent);
                for (TableDefinition table : tables) {
                    if (tableNames.contains(table.getTableName())) {
                        scriptTables.add(table);
                    }
                }
            }

            Map<String, DbTableInfo> dbTables =
                    metadataExtractor.extractTableMetadata(conn, tableNames);
            List<DbSchemaComparator.SchemaDiff> schemaDiffs =
                    dbSchemaComparator.compare(scriptTables, dbTables);

            syncSqls = generateSyncSqls(scriptTables, dbTables, schemaDiffs);
        } catch (Exception e) {
            log.error("生成选定表同步 SQL 失败", e);
        } finally {
            if (closeConn && conn != null) {
                dbCheckUtils.closeQuietly(conn);
            }
        }

        return syncSqls;
    }

    /**
     * 生成同步 SQL
     */
    private List<String> generateSyncSqls(List<TableDefinition> scriptTables,
                                           Map<String, DbTableInfo> dbTables,
                                           List<DbSchemaComparator.SchemaDiff> schemaDiffs) {
        List<String> sqls = new ArrayList<>();
        for (DbSchemaComparator.SchemaDiff diff : schemaDiffs) {
            switch (diff.getType()) {
                case "ONLY_IN_SCRIPT":
                    // 生成 CREATE TABLE
                    for (TableDefinition def : scriptTables) {
                        if (def.getTableName().equals(diff.getTableName())) {
                            sqls.add(def.getOriginalSql());
                            break;
                        }
                    }
                    break;
                case "MISSING_COLUMN":
                    // 生成 ALTER TABLE ADD COLUMN
                    String addSql = generateAddColumnSql(diff.getTableName(), diff.getColumnName(),
                            findColumnDef(scriptTables, diff.getTableName(), diff.getColumnName()));
                    if (addSql != null) {
                        sqls.add(addSql);
                    }
                    break;
                case "COLUMN_TYPE_MISMATCH":
                    // 生成 ALTER TABLE MODIFY COLUMN
                    String modifySql = generateModifyColumnSql(diff.getTableName(), diff.getColumnName(),
                            findColumnDef(scriptTables, diff.getTableName(), diff.getColumnName()));
                    if (modifySql != null) {
                        sqls.add(modifySql);
                    }
                    break;
                default:
                    break;
            }
        }
        return sqls;
    }

    private String generateAddColumnSql(String tableName, String columnName,
                                         TableColumnDefinition columnDef) {
        if (columnDef == null) return null;
        return "ALTER TABLE " + tableName + " ADD " + columnName + " " + columnDef.getColumnType()
                + (columnDef.isNullable() ? "" : " NOT NULL") + ";";
    }

    private String generateModifyColumnSql(String tableName, String columnName,
                                            TableColumnDefinition columnDef) {
        if (columnDef == null) return null;
        return "ALTER TABLE " + tableName + " MODIFY " + columnName + " " + columnDef.getColumnType()
                + (columnDef.isNullable() ? "" : " NOT NULL") + ";";
    }

    private TableColumnDefinition findColumnDef(List<TableDefinition> scriptTables,
                                                 String tableName, String columnName) {
        for (TableDefinition def : scriptTables) {
            if (def.getTableName().equals(tableName)) {
                for (TableColumnDefinition col : def.getColumns()) {
                    if (col.getColumnName().equals(columnName)) {
                        return col;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 执行同步（将同步 SQL 应用到数据库）
     */
    public DbCheckResult.SyncResult executeSync(Connection conn, List<String> sqls) {
        return schemaSyncService.syncSchema(conn, sqls);
    }
}
