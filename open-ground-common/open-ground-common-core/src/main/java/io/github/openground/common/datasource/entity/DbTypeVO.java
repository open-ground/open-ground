package io.github.openground.common.datasource.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * 数据库类型 VO
 *
 * <p>用于返回可选的数据库类型列表，含驱动类和 URL 示例。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Data
@AllArgsConstructor
@Schema(description = "数据库类型信息")
public class DbTypeVO {

    @Schema(description = "数据库类型标识（小写英文）")
    private String value;

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "默认驱动类名")
    private String defaultDriver;

    @Schema(description = "URL 示例")
    private String urlExample;

    /**
     * 数据库类型目录
     *
     * @return 完整的数据库类型列表
     */
    public static List<DbTypeVO> allTypes() {
        return Arrays.asList(
                // ===== 国际常见数据库 =====
                new DbTypeVO("mysql", "MySQL", "com.mysql.cj.jdbc.Driver",
                        "jdbc:mysql://localhost:3306/mydb?useUnicode=true&characterEncoding=utf-8"),
                new DbTypeVO("oracle", "Oracle", "oracle.jdbc.OracleDriver",
                        "jdbc:oracle:thin:@localhost:1521:orcl"),
                new DbTypeVO("postgresql", "PostgreSQL", "org.postgresql.Driver",
                        "jdbc:postgresql://localhost:5432/mydb"),
                new DbTypeVO("mssql", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                        "jdbc:sqlserver://localhost:1433;databaseName=mydb"),
                new DbTypeVO("db2", "DB2", "com.ibm.db2.jcc.DB2Driver",
                        "jdbc:db2://localhost:50000/mydb"),
                new DbTypeVO("sqlite", "SQLite", "org.sqlite.JDBC",
                        "jdbc:sqlite:/path/to/mydb.db"),
                // ===== 国产数据库 =====
                new DbTypeVO("dm", "达梦 DM", "dm.jdbc.driver.DmDriver",
                        "jdbc:dm://localhost:5236/mydb"),
                new DbTypeVO("gaussdb", "GaussDB", "com.huawei.gaussdb.jdbc.Driver",
                        "jdbc:gaussdb://localhost:8000/mydb"),
                new DbTypeVO("kingbase", "KingbaseES", "com.kingbase8.Driver",
                        "jdbc:kingbase8://localhost:54321/mydb"),
                new DbTypeVO("oceanbase", "OceanBase", "com.oceanbase.jdbc.Driver",
                        "jdbc:oceanbase://localhost:2883/mydb"),
                new DbTypeVO("tidb", "TiDB", "com.mysql.cj.jdbc.Driver",
                        "jdbc:mysql://localhost:4000/mydb?useUnicode=true&characterEncoding=utf-8"),
                new DbTypeVO("tbase", "TBase", "org.postgresql.Driver",
                        "jdbc:postgresql://localhost:15432/mydb")
        );
    }
}
