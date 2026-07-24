package io.github.openground.common.jdbc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 多数据源配置属性
 *
 * <p>对应配置文件中的 {@code ground.dblist} 列表。
 * 每个条目定义一个数据源连接信息。
 *
 * <pre>
 * ground:
 *   dblist:
 *     - dsName: master
 *       dbName: auth_db
 *       app: auth
 *       url: jdbc:mysql://localhost:3306/auth_db
 *       username: root
 *       password: 123456
 *       driverClassName: com.mysql.cj.jdbc.Driver
 *       dbType: mysql
 *   dsService: mySqlDataSource  # 数据源类型标识（可选）
 * </pre>
 *
 * @author open-ground
 */
@Data
@ConfigurationProperties(prefix = "ground")
public class DynamicDataSourceProperties {

    /** 多数据源列表 */
    private List<DataSourceEntry> dblist;

    /** 数据源服务类型（如 mySqlDataSource, oracleDataSource 等），用于数据库类型判断 */
    private String dsService = "mySqlDataSource";

    @Data
    public static class DataSourceEntry {

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

        /** 密码 */
        private String password;

        /** JDBC 驱动类名（可选，默认自动推断） */
        private String driverClassName;

        /** 数据库类型（可选，不配则自动推断）：mysql/oracle/postgresql/dm/gaussdb */
        private String dbType;

        /** 连接池初始化大小（可选，默认 5） */
        private int initialSize = 5;

        /** 连接池最大活跃数（可选，默认 20） */
        private int maxActive = 20;

        /** 连接池最小空闲数（可选，默认 5） */
        private int minIdle = 5;

        /** 连接超时时间（毫秒，可选，默认 30000） */
        private long maxWait = 30000;
    }
}
