package io.github.openground.land.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 调度框架自有数据源配置
 * <p>
 * 框架使用独立的数据源（ground.land.datasource），与业务系统的 spring.datasource 完全隔离。
 * TASK_DISPATCH_* 等框架调度表存储在此数据源中，业务系统无需关心。
 * </p>
 * <pre>
 * ground:
 *   land:
 *     datasource:
 *       url: jdbc:mysql://host:3306/land_framework
 *       username: land
 *       password: land123
 *       driver-class-name: com.mysql.cj.jdbc.Driver
 *       initial-size: 5
 *       max-active: 20
 *       min-idle: 2
 *       max-wait: 10000
 *       validation-query: SELECT 'X' FROM DUAL
 * </pre>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Slf4j
@AutoConfiguration
@MapperScan(
    basePackages = "io.github.openground.land.mapper",
    sqlSessionFactoryRef = "landSqlSessionFactory"
)
public class LandDataSourceConfig {

    /**
     * 框架自有数据源
     * <p>从 ground.land.datasource 前缀读取配置，Druid 连接池</p>
     */
    @Bean("landDataSource")
    // @ConditionalOnMissingBean(name = "landDataSource")
    @ConfigurationProperties(prefix = "ground.land.datasource")
    public DataSource landDataSource() {
        log.info("初始化调度框架数据源: ground.land.datasource");
        return new DruidDataSource();
    }

    /**
     * 框架 SqlSessionFactory
     * <p>扫描 classpath:mapper/land/*.xml 下的框架 Mapper XML</p>
     */
    @Bean("landSqlSessionFactory")
    // @ConditionalOnMissingBean(name = "landSqlSessionFactory")
    public SqlSessionFactory landSqlSessionFactory(@Qualifier("landDataSource") javax.sql.DataSource landDataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(landDataSource);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // land-core 模块的 Mapper XML（classpath:mapper/land/*.xml）
        org.springframework.core.io.Resource[] landXmls = resolver.getResources("classpath*:mapper/land/*.xml");
        // DMP 模块的 Mapper XML（classpath:mapper/Dmp*.xml）
        org.springframework.core.io.Resource[] dmpXmls = resolver.getResources("classpath*:mapper/Dmp*.xml");
        // 合并
        org.springframework.core.io.Resource[] all = new org.springframework.core.io.Resource[landXmls.length + dmpXmls.length];
        System.arraycopy(landXmls, 0, all, 0, landXmls.length);
        System.arraycopy(dmpXmls, 0, all, landXmls.length, dmpXmls.length);
        factory.setMapperLocations(all);
        log.info("初始化调度框架 SqlSessionFactory");
        return factory.getObject();
    }

    /**
     * 框架 SqlSessionTemplate
     */
    @Bean("landSqlSessionTemplate")
   // @ConditionalOnMissingBean(name = "landSqlSessionTemplate")
    public SqlSessionTemplate landSqlSessionTemplate(@Qualifier("landSqlSessionFactory") SqlSessionFactory landSqlSessionFactory) {
        return new SqlSessionTemplate(landSqlSessionFactory);
    }

    /**
     * 框架事务管理器
     * <p>保留以备将来需要编程式事务时使用，当前调度引擎不需要事务</p>
     */
    @Bean("landTransactionManager")
    // @ConditionalOnMissingBean(name = "landTransactionManager")
    public DataSourceTransactionManager landTransactionManager(@Qualifier("landDataSource") javax.sql.DataSource landDataSource) {
        return new DataSourceTransactionManager(landDataSource);
    }
}
