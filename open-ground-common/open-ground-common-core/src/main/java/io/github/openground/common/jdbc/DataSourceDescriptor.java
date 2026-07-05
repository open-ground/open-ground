package io.github.openground.common.jdbc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源描述符
 *
 * <p>统一的数据源信息模型，无论来源是 dblist 配置还是 sys_datasource 表，
 * 都转换为此对象供 {@link DynamicDataSourceManager} 使用。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceDescriptor {

    /** 数据源名称（唯一标识） */
    private String dsName;

    /** 数据库名称 */
    private String dbName;

    /** 应用标识（用于数据源分类，如 auth/pub/dmp） */
    private String app;

    /** JDBC URL */
    private String url;

    /** 用户名 */
    private String username;

    /** 密码（明文，各 Provider 自行解密后传入） */
    private String password;

    /** JDBC 驱动类名 */
    private String driverClassName;

    /** 数据库类型（mysql/oracle/postgresql/dm/gaussdb 等，可选，不配则自动推断） */
    private String dbType;

    /** 来源标识：config / sys_datasource */
    private String source;

    /** 原始实体对象（如 SysDatasourceDO，供扩展使用） */
    private Object rawEntity;
}
