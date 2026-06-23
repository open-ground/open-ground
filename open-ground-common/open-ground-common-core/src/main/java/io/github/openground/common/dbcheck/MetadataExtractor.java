package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库元数据提取器
 * 直接从数据库元数据获取表结构，避免解析 SQL
 * <p>批量获取所有表的列信息，避免 N+1 查询问题。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Component
public class MetadataExtractor {

    private final DataSource dataSource;

    public MetadataExtractor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 获取数据库中所有表的信息（使用内置数据源）
     */
    public List<DbTableInfo> getAllTables() {
        try (Connection conn = dataSource.getConnection()) {
            return getAllTables(conn);
        } catch (Exception e) {
            log.error("Failed to extract database metadata", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取数据库中所有表的信息（使用外部连接）
     * <p>批量获取列、主键、索引、自增信息。
     */
    public List<DbTableInfo> getAllTables(Connection conn) {
        List<DbTableInfo> tables = new ArrayList<>();

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String connCatalog = conn.getCatalog();
            String connSchema = conn.getSchema();

            // 1. 一次查询获取所有表名+注释
            List<DbTableInfo> tableInfos = new ArrayList<>();
            String rawCatalog = connCatalog;
            String rawSchema = connSchema;
            // 从 getTables 结果推断 catalog/schema 策略：记录首行的 TABLE_CAT 和 TABLE_SCHEM
            String firstTableCat = null;
            String firstTableSchem = null;
            try (ResultSet tablesRs = metaData.getTables(rawCatalog, rawSchema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    DbTableInfo tableInfo = new DbTableInfo();
                    tableInfo.setTableName(tablesRs.getString("TABLE_NAME"));
                    tableInfo.setSchema(tablesRs.getString("TABLE_SCHEM"));
                    tableInfo.setType("TABLE");
                    tableInfo.setComment(tablesRs.getString("REMARKS"));
                    tableInfos.add(tableInfo);

                    // 记录首行的 catalog 和 schema（用于判定元数据 API 的传参方式）
                    if (firstTableCat == null) {
                        firstTableCat = tablesRs.getString("TABLE_CAT");
                    }
                    if (firstTableSchem == null) {
                        firstTableSchem = tablesRs.getString("TABLE_SCHEM");
                    }
                }
            }

            // 动态推断 catalog/schema 策略：
            //   catalog-based（MySQL / TDSQL / OceanBase MySQL 模式）：
            //     TABLE_CAT 非空且 TABLE_SCHEM 为空 → 后续 API 用 (catalog, null, ...)
            //   schema-based（PostgreSQL / GaussDB / Oracle / DM / OceanBase Oracle 模式）：
            //     TABLE_SCHEM 非空 → 后续 API 用 (null, schema, ...)
            boolean catalogBased = (firstTableCat != null && !firstTableCat.isEmpty())
                    && (firstTableSchem == null || firstTableSchem.isEmpty());
            String useCatalog = catalogBased ? firstTableCat : null;
            String useSchema = catalogBased ? null : firstTableSchem;
            log.debug("JDBC metadata strategy: catalogBased={}, useCatalog={}, useSchema={}",
                    catalogBased, useCatalog, useSchema);

            // 2. 一次 getColumns 获取所有列，按表名分组
            Map<String, List<DbColumnInfo>> columnsByTable = new LinkedHashMap<>();
            try (ResultSet rs = metaData.getColumns(useCatalog, useSchema, "%", "%")) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName == null) continue;

                    DbColumnInfo column = new DbColumnInfo();
                    column.setName(rs.getString("COLUMN_NAME"));
                    column.setType(rs.getString("TYPE_NAME"));
                    column.setSize(rs.getInt("COLUMN_SIZE"));
                    column.setNullable(rs.getString("IS_NULLABLE"));
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    column.setComment(rs.getString("REMARKS"));
                    // 自增列：IS_AUTOINCREMENT 是 JDBC 4.0 标准列
                    String autoInc = rs.getString("IS_AUTOINCREMENT");
                    column.setAutoIncrement("YES".equalsIgnoreCase(autoInc));

                    columnsByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(column);
                }
            }

            // 3. 读取主键信息（先尝试 JDBC API，失败后回退到 information_schema）
            Map<String, List<String>> primaryKeysByTable = new LinkedHashMap<>();
            // 3a. JDBC API
            String[][] pkParams = {
                {useCatalog, useSchema},
                {null, firstTableCat},
                {firstTableSchem, null}
            };
            for (int pi = 0; pi < pkParams.length; pi++) {
                String cat = pkParams[pi][0];
                String sch = pkParams[pi][1];
                if (cat == null && sch == null) continue;
                if (!primaryKeysByTable.isEmpty()) break;
                try (ResultSet pkRs = metaData.getPrimaryKeys(cat, sch, "%")) {
                    while (pkRs.next()) {
                        String tableName = pkRs.getString("TABLE_NAME");
                        String colName = pkRs.getString("COLUMN_NAME");
                        if (tableName == null) continue;
                        primaryKeysByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(colName);
                    }
                } catch (Exception ignored) { }
            }
            // 3b. information_schema 回退（兼容 MySQL 系列驱动缺陷）
            if (primaryKeysByTable.isEmpty() && firstTableCat != null && !firstTableCat.isEmpty()) {
                String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = ? AND CONSTRAINT_NAME = 'PRIMARY' ORDER BY TABLE_NAME, ORDINAL_POSITION";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, firstTableCat);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tableName = rs.getString("TABLE_NAME");
                            String colName = rs.getString("COLUMN_NAME");
                            if (tableName == null) continue;
                            primaryKeysByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(colName);
                        }
                    }
                } catch (Exception e) {
                    log.debug("information_schema not available for primary keys (non-MySQL): {}", e.getMessage());
                }
            }

            // 4. 读取索引信息
            //    策略：按数据库类型使用最可靠的方式读取，避免 JDBC getIndexInfo() 驱动兼容性问题
            Map<String, List<DbIndexInfo>> indexesByTable = new LinkedHashMap<>();

            // 从连接元数据检测数据库类型
            String dbProductName = metaData.getDatabaseProductName();
            String dbProductNameLower = dbProductName != null ? dbProductName.toLowerCase() : "";
            if (dbProductNameLower.contains("mysql")) {
                // MySQL：information_schema.STATISTICS 最可靠
                readMysqlIndexes(conn, firstTableCat, primaryKeysByTable, indexesByTable);
            } else if (dbProductNameLower.contains("postgresql") || dbProductNameLower.contains("gaussdb")) {
                // PostgreSQL / GaussDB：JDBC API + pg_catalog 回退
                readPostgresqlIndexes(metaData, conn, useCatalog, useSchema, firstTableCat,
                        firstTableSchem, primaryKeysByTable, indexesByTable);
            } else if (dbProductNameLower.contains("oracle") || dbProductNameLower.contains("dm")) {
                // Oracle / 达梦：JDBC API + all_ind_columns 回退
                readOracleIndexes(metaData, conn, useCatalog, useSchema, firstTableCat,
                        firstTableSchem, primaryKeysByTable, indexesByTable);
            } else {
                // 未知数据库：仅 JDBC API（多策略 + 日志）
                readIndexesFromJdbcApi(metaData, useCatalog, useSchema, firstTableCat,
                        firstTableSchem, primaryKeysByTable, indexesByTable);
            }

            // 5. 组装表信息
            for (DbTableInfo tableInfo : tableInfos) {
                String tn = tableInfo.getTableName();
                List<DbColumnInfo> cols = columnsByTable.get(tn);
                if (cols != null) {
                    tableInfo.setColumns(cols);
                }

                // 标记主键列
                List<String> pkCols = primaryKeysByTable.get(tn);
                if (pkCols != null && !pkCols.isEmpty()) {
                    tableInfo.setPrimaryKeyColumns(pkCols);
                    if (cols != null) {
                        for (DbColumnInfo col : cols) {
                            if (pkCols.contains(col.getName())) {
                                col.setPrimaryKey(true);
                            }
                        }
                    }
                }

                // 设置索引
                List<DbIndexInfo> idxList = indexesByTable.get(tn);
                if (idxList != null && !idxList.isEmpty()) {
                    tableInfo.setIndexList(idxList);
                }

                tables.add(tableInfo);
            }
        } catch (Exception e) {
            log.error("Failed to extract database metadata", e);
        }

        return tables;
    }

    /**
     * 通过 information_schema.STATISTICS 读取索引信息（MySQL 专用）
     * <p>MySQL JDBC 驱动的 getIndexInfo() 在各版本中行为不一致，
     * 直接查 information_schema 更可靠。
     */
    private void readMysqlIndexes(Connection conn, String schemaName,
                                   Map<String, List<String>> primaryKeysByTable,
                                   Map<String, List<DbIndexInfo>> indexesByTable) {
        if (schemaName == null || schemaName.isEmpty()) {
            log.warn("schemaName is null/empty, cannot read MySQL indexes");
            return;
        }
        doReadIndexesFromSql(conn,
                "SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX "
                + "FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX",
                schemaName, primaryKeysByTable, indexesByTable);
    }

    /**
     * 通过 pg_catalog 读取索引信息（PostgreSQL / GaussDB 专用）
     * <p>先尝试 JDBC API，失败时用 pg_catalog 查询回退。
     */
    private void readPostgresqlIndexes(DatabaseMetaData metaData, Connection conn,
                                        String useCatalog, String useSchema,
                                        String firstTableCat, String firstTableSchem,
                                        Map<String, List<String>> primaryKeysByTable,
                                        Map<String, List<DbIndexInfo>> indexesByTable) {
        // 先尝试 JDBC API
        readIndexesFromJdbcApi(metaData, useCatalog, useSchema, firstTableCat,
                firstTableSchem, primaryKeysByTable, indexesByTable);

        // 如果 JDBC API 没读到，尝试 pg_catalog 查询
        if (indexesByTable.isEmpty()) {
            String pgSchema = useSchema != null ? useSchema : firstTableSchem;
            if (pgSchema == null) pgSchema = "public";
            log.info("JDBC API returned no indexes for PostgreSQL, trying pg_catalog with schema={}", pgSchema);
            readIndexesFromPgCatalog(conn, pgSchema, primaryKeysByTable, indexesByTable);
        }
    }

    /**
     * PostgreSQL pg_catalog 索引查询
     */
    private void readIndexesFromPgCatalog(Connection conn, String schemaName,
                                           Map<String, List<String>> primaryKeysByTable,
                                           Map<String, List<DbIndexInfo>> indexesByTable) {
        String sql = "SELECT c.relname AS TABLE_NAME, i.relname AS INDEX_NAME, "
                + "a.attname AS COLUMN_NAME, "
                + "CASE WHEN ix.indisunique THEN 0 ELSE 1 END AS NON_UNIQUE, "
                + "row_number() OVER (PARTITION BY i.relname ORDER BY a.attnum) AS SEQ_IN_INDEX "
                + "FROM pg_class c "
                + "JOIN pg_index ix ON c.oid = ix.indrelid "
                + "JOIN pg_class i ON ix.indexrelid = i.oid "
                + "JOIN pg_attribute a ON c.oid = a.attrelid AND a.attnum = ANY(ix.indkey) "
                + "JOIN pg_namespace n ON c.relnamespace = n.oid "
                + "WHERE c.relkind = 'r' AND n.nspname = ? "
                + "ORDER BY c.relname, i.relname, a.attnum";
        doReadIndexesFromSql(conn, sql, schemaName, primaryKeysByTable, indexesByTable);
    }

    /**
     * 通过 all_ind_columns / all_indexes 读取索引信息（Oracle / 达梦专用）
     * <p>先尝试 JDBC API，失败时用 all_ind_columns 查询回退。
     */
    private void readOracleIndexes(DatabaseMetaData metaData, Connection conn,
                                    String useCatalog, String useSchema,
                                    String firstTableCat, String firstTableSchem,
                                    Map<String, List<String>> primaryKeysByTable,
                                    Map<String, List<DbIndexInfo>> indexesByTable) {
        // 先尝试 JDBC API
        readIndexesFromJdbcApi(metaData, useCatalog, useSchema, firstTableCat,
                firstTableSchem, primaryKeysByTable, indexesByTable);

        // 如果 JDBC API 没读到，尝试 all_ind_columns 查询
        if (indexesByTable.isEmpty()) {
            String owner = firstTableSchem != null ? firstTableSchem : useSchema;
            if (owner == null && firstTableCat != null) owner = firstTableCat;
            if (owner != null) {
                log.info("JDBC API returned no indexes for Oracle/DM, trying all_ind_columns with owner={}", owner);
                readIndexesFromAllIndColumns(conn, owner, primaryKeysByTable, indexesByTable);
            }
        }
    }

    /**
     * Oracle / DM all_ind_columns 索引查询
     */
    private void readIndexesFromAllIndColumns(Connection conn, String ownerName,
                                               Map<String, List<String>> primaryKeysByTable,
                                               Map<String, List<DbIndexInfo>> indexesByTable) {
        String sql = "SELECT ic.table_name AS TABLE_NAME, ic.index_name AS INDEX_NAME, "
                + "ic.column_name AS COLUMN_NAME, "
                + "CASE WHEN i.uniqueness = 'UNIQUE' THEN 0 ELSE 1 END AS NON_UNIQUE, "
                + "ic.column_position AS SEQ_IN_INDEX "
                + "FROM all_ind_columns ic "
                + "JOIN all_indexes i ON ic.index_name = i.index_name AND ic.table_owner = i.table_owner "
                + "WHERE ic.table_owner = ? "
                + "ORDER BY ic.table_name, ic.index_name, ic.column_position";
        doReadIndexesFromSql(conn, sql, ownerName, primaryKeysByTable, indexesByTable);
    }

    /**
     * 公共方法：执行 SQL 查询读取索引并展平为 DbIndexInfo
     */
    private void doReadIndexesFromSql(Connection conn, String sql, String schemaParam,
                                      Map<String, List<String>> primaryKeysByTable,
                                      Map<String, List<DbIndexInfo>> indexesByTable) {
        Map<String, List<String>> indexColumnsByKey = new LinkedHashMap<>();
        Map<String, Boolean> indexUniqueByKey = new LinkedHashMap<>();

        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaParam);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    if (tableName == null || indexName == null || colName == null) continue;

                    // 跳过主键索引（PRIMARY 或 PK_ 开头的命名主键）
                    // 注意：不逐列过滤 PK 列——复合索引（如 UNIQUE INDEX (pk_col, other_col)）
                    // 中包含 PK 列是正常情况，逐列过滤会导致索引列不完整。
                    String upperIdx = indexName.toUpperCase();
                    if (upperIdx.contains("PRIMARY") || upperIdx.startsWith("PK_")) {
                        continue;
                    }

                    String key = tableName + "|" + indexName;
                    indexColumnsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(colName);
                    if (!indexUniqueByKey.containsKey(key)) {
                        // NON_UNIQUE = 0 表示唯一，1 表示非唯一
                        String nonUnique = rs.getString("NON_UNIQUE");
                        indexUniqueByKey.put(key, "0".equals(nonUnique));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read indexes from SQL: {}", e.getMessage());
            return;
        }

        // 展平为 DbIndexInfo
        for (Map.Entry<String, List<String>> entry : indexColumnsByKey.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String tName = parts[0];
            String idxName = parts[1];
            List<String> cols = entry.getValue();
            boolean unique = indexUniqueByKey.getOrDefault(entry.getKey(), false);
            DbIndexInfo idxInfo = new DbIndexInfo();
            idxInfo.setIndexName(idxName);
            idxInfo.setColumnNames(cols);
            idxInfo.setUnique(unique);
            indexesByTable.computeIfAbsent(tName, k -> new ArrayList<>()).add(idxInfo);
        }
        log.info("Read {} indexes from SQL query", indexColumnsByKey.size());
    }

    /**
     * 兜底：通过 JDBC DatabaseMetaData.getIndexInfo() 读取索引
     * <p>尝试多组 catalog/schema 参数组合。
     */
    private void readIndexesFromJdbcApi(DatabaseMetaData metaData,
                                         String useCatalog, String useSchema,
                                         String firstTableCat, String firstTableSchem,
                                         Map<String, List<String>> primaryKeysByTable,
                                         Map<String, List<DbIndexInfo>> indexesByTable) {
        Map<String, List<String>> indexColumnsByKey = new LinkedHashMap<>();
        Map<String, Boolean> indexUniqueByKey = new LinkedHashMap<>();

        // JDBC API：尝试多组参数组合
        String[][] idxParams = {
            {useCatalog, useSchema},
            {null, firstTableCat},
            {firstTableSchem, null}
        };
        for (int pi = 0; pi < idxParams.length; pi++) {
            String cat = idxParams[pi][0];
            String sch = idxParams[pi][1];
            if (cat == null && sch == null) continue;
            if (!indexColumnsByKey.isEmpty()) break;
            try (ResultSet idxRs = metaData.getIndexInfo(cat, sch, "%", false, false)) {
                while (idxRs.next()) {
                    String tableName = idxRs.getString("TABLE_NAME");
                    if (tableName == null) continue;
                    String indexName = idxRs.getString("INDEX_NAME");
                    if (indexName == null) continue;
                    String colName = idxRs.getString("COLUMN_NAME");
                    if (colName == null) continue;
                    short idxType = idxRs.getShort("TYPE");
                    if (idxType == DatabaseMetaData.tableIndexStatistic) continue;
                    List<String> pkCols = primaryKeysByTable.get(tableName);
                    if (pkCols != null) {
                        if (pkCols.contains(colName)) {
                            String upperIdx = indexName.toUpperCase();
                            if (upperIdx.contains("PRIMARY") || upperIdx.startsWith("PK_")) {
                                continue;
                            }
                        }
                    }
                    String key = tableName + "|" + indexName;
                    indexColumnsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(colName);
                    if (!indexUniqueByKey.containsKey(key)) {
                        indexUniqueByKey.put(key, !idxRs.getBoolean("NON_UNIQUE"));
                    }
                }
            } catch (Exception e) {
                log.warn("getIndexInfo failed with params catalog={}, schema={}: {}",
                        cat, sch, e.getMessage());
            }
        }

        // 展平为 DbIndexInfo
        for (Map.Entry<String, List<String>> entry : indexColumnsByKey.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String tName = parts[0];
            String idxName = parts[1];
            List<String> cols = entry.getValue();
            boolean unique = indexUniqueByKey.getOrDefault(entry.getKey(), false);
            DbIndexInfo idxInfo = new DbIndexInfo();
            idxInfo.setIndexName(idxName);
            idxInfo.setColumnNames(cols);
            idxInfo.setUnique(unique);
            indexesByTable.computeIfAbsent(tName, k -> new ArrayList<>()).add(idxInfo);
        }
        log.info("Read {} indexes from JDBC API", indexColumnsByKey.size());
    }

    // ========== 内部实体类 ==========

    public static class DbTableInfo {
        private String tableName;
        private String schema;
        private String type;
        private String comment;
        private List<DbColumnInfo> columns = new ArrayList<>();
        /** 主键列名列表（按顺序） */
        private List<String> primaryKeyColumns = new ArrayList<>();
        /** 索引列表 */
        private List<DbIndexInfo> indexList = new ArrayList<>();

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public List<DbColumnInfo> getColumns() { return columns; }
        public void setColumns(List<DbColumnInfo> columns) { this.columns = columns; }
        public List<String> getPrimaryKeyColumns() { return primaryKeyColumns; }
        public void setPrimaryKeyColumns(List<String> primaryKeyColumns) { this.primaryKeyColumns = primaryKeyColumns; }
        public List<DbIndexInfo> getIndexList() { return indexList; }
        public void setIndexList(List<DbIndexInfo> indexList) { this.indexList = indexList; }
    }

    public static class DbColumnInfo {
        private String name;
        private String type;
        private int size;
        private String nullable;
        private String defaultValue;
        private String comment;
        private boolean primaryKey;
        private boolean autoIncrement;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getNullable() { return nullable; }
        public void setNullable(String nullable) { this.nullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
        public boolean isAutoIncrement() { return autoIncrement; }
        public void setAutoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; }
    }

    /**
     * 索引信息
     */
    public static class DbIndexInfo {
        private String indexName;
        private List<String> columnNames = new ArrayList<>();
        private boolean unique;

        public String getIndexName() { return indexName; }
        public void setIndexName(String indexName) { this.indexName = indexName; }
        public List<String> getColumnNames() { return columnNames; }
        public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
    }
}
