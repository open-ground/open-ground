package io.github.openground.land.common.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Druid 连接池配置属性
 * <p>从 spring.datasource.druid 前缀读取 Druid 连接池参数，用于动态创建数据源。</p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Component
@ConfigurationProperties(prefix = "spring.datasource.druid")
@Data
public class DruidProperties {

    /** 初始化时建立物理连接的个数 */
    private int initialSize = 1;

    /** 最小连接池数量 */
    private int minIdle = 0;

    /** 最大连接池数量 */
    private int maxActive = 10;

    /** 获取连接时最大等待时间，单位毫秒 */
    private int maxWait = 3000;

    /** 关闭空闲连接的检测时间间隔 */
    private int timeBetweenEvictionRunsMillis = 6000;

    /** 连接的最小生存时间 */
    private int minEvictableIdleTimeMillis = 300000;

    /** 申请连接时检测空闲时间 */
    private boolean testWhileIdle = true;

    /** 开启 PSCache */
    private boolean poolPreparedStatements = true;

    /** PSCache 值 */
    private int maxPoolPreparedStatementPerConnectionSize = 20;

    /** 连接出错后再尝试连接次数 */
    private int connectionErrorRetryAttempts = 3;

    /** 数据库服务宕机自动重连机制 */
    private boolean breakAfterAcquireFailure = true;

    /** 连接出错后重试时间间隔 */
    private int timeBetweenConnectErrorMillis = 300000;

    /** 验证连接有效性 SQL */
    private String validationQuery;

    /**
     * 根据参数创建 Druid 数据源
     */
    public DruidDataSource dataSource(String url, String username, String password, String driverClassName) {
        DruidDataSource datasource = new DruidDataSource();
        datasource.setUrl(url);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setDriverClassName(driverClassName);
        datasource.setInitialSize(initialSize);
        datasource.setMinIdle(minIdle);
        datasource.setMaxActive(maxActive);
        datasource.setMaxWait(maxWait);
        datasource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        datasource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        datasource.setTestWhileIdle(testWhileIdle);
        datasource.setPoolPreparedStatements(poolPreparedStatements);
        datasource.setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);
        datasource.setConnectionErrorRetryAttempts(connectionErrorRetryAttempts);
        datasource.setBreakAfterAcquireFailure(breakAfterAcquireFailure);
        datasource.setTimeBetweenConnectErrorMillis(timeBetweenConnectErrorMillis);
        datasource.setValidationQuery(validationQuery);
        return datasource;
    }
}
