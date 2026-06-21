package io.github.openground.common.dbcheck;

import java.sql.Connection;
import java.util.List;

/**
 * DbCheck 数据源提供者 SPI 接口
 *
 * <p>允许外部模块（如 DMP）注册可检查的数据源。
 * open-ground-core 不依赖任何外部模块，通过此接口实现松耦合集成。
 *
 * <p>实现类只需声明为 Spring Bean，DbCheckController 会自动收集并展示。
 *
 * @author open-ground
 * @since 2026-06-18
 */
public interface DbCheckDatasourceProvider {

    /**
     * 列出该 Provider 管理的所有数据源
     *
     * @return 数据源信息列表
     */
    List<DatasourceInfo> listDatasources();

    /**
     * 获取数据源的 JDBC 连接（调用方负责关闭）
     *
     * @param datasourceId 数据源ID
     * @return JDBC 连接
     */
    Connection getConnection(Long datasourceId);

    /**
     * 获取数据源的数据库类型
     *
     * @param datasourceId 数据源ID
     * @return 数据库类型（mysql/oracle/postgresql 等）
     */
    String getDbType(Long datasourceId);

    /**
     * 在指定数据源上执行 SQL 语句（用于手动同步）
     *
     * @param datasourceId 数据源ID
     * @param sqls         要执行的 SQL 列表
     */
    default void executeSqls(Long datasourceId, List<String> sqls) {
        throw new UnsupportedOperationException("executeSqls not supported by this provider");
    }

    /**
     * 数据源信息 DTO
     */
    class DatasourceInfo {
        private Long id;
        private String name;
        private String dbType;
        private String source;

        public DatasourceInfo() {}

        public DatasourceInfo(Long id, String name, String dbType, String source) {
            this.id = id;
            this.name = name;
            this.dbType = dbType;
            this.source = source;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
