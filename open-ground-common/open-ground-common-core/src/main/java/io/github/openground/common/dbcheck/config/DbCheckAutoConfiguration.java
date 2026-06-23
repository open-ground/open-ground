package io.github.openground.common.dbcheck.config;

import io.github.openground.common.dbcheck.service.DataSyncService;
import io.github.openground.common.dbcheck.util.DatabaseTypeDetector;
import io.github.openground.common.dbcheck.model.DbCheckProperties;
import io.github.openground.common.dbcheck.service.DbCheckService;
import io.github.openground.common.dbcheck.extractor.MetadataExtractor;
import io.github.openground.common.dbcheck.service.SchemaSyncService;
import io.github.openground.common.dbcheck.extractor.ScriptPathResolver;
import io.github.openground.common.dbcheck.extractor.SimpleSqlParser;
import io.github.openground.common.dbcheck.extractor.SqlScriptScanner;
import io.github.openground.common.dbcheck.checker.CommentChecker;
import io.github.openground.common.dbcheck.checker.DataChecker;
import io.github.openground.common.dbcheck.checker.DbSchemaComparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * DbCheck 自动配置类
 * 集成到 Spring Boot 启动流程
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DbCheckProperties.class)
@ConditionalOnProperty(name = "ground.db-check.enabled", havingValue = "true", matchIfMissing = true)
public class DbCheckAutoConfiguration {

    private final DbCheckProperties dbCheckProperties;

    public DbCheckAutoConfiguration(DbCheckProperties dbCheckProperties) {
        this.dbCheckProperties = dbCheckProperties;
    }

    /**
     * 创建 DbCheck 主服务
     */
    @Bean
    public DbCheckService dbCheckService(DatabaseTypeDetector databaseTypeDetector,
                                         ScriptPathResolver scriptPathResolver,
                                         SqlScriptScanner sqlScriptScanner,
                                         SimpleSqlParser simpleSqlParser,
                                         MetadataExtractor metadataExtractor,
                                         DbSchemaComparator dbSchemaComparator,
                                         DataChecker dataChecker,
                                         SchemaSyncService schemaSyncService,
                                         DataSyncService dataSyncService,
                                         CommentChecker commentChecker) {
        return new DbCheckService(dbCheckProperties, databaseTypeDetector, scriptPathResolver,
                sqlScriptScanner, simpleSqlParser, metadataExtractor, dbSchemaComparator, 
                dataChecker, schemaSyncService, dataSyncService, commentChecker);
    }

    /**
     * 创建数据库类型检测器
     */
    @Bean
    public DatabaseTypeDetector databaseTypeDetector() {
        return new DatabaseTypeDetector();
    }

    /**
     * 创建脚本路径解析器
     */
    @Bean
    public ScriptPathResolver scriptPathResolver() {
        return new ScriptPathResolver();
    }

    /**
     * 创建 SQL 脚本扫描器
     */
    @Bean
    public SqlScriptScanner sqlScriptScanner(ScriptPathResolver scriptPathResolver,
                                           DatabaseTypeDetector databaseTypeDetector,
                                           DbCheckProperties dbCheckProperties) {
        return new SqlScriptScanner(scriptPathResolver, databaseTypeDetector, dbCheckProperties);
    }

    /**
     * 创建简化 SQL 解析器
     */
    @Bean
    public SimpleSqlParser simpleSqlParser() {
        return new SimpleSqlParser();
    }

    /**
     * 创建元数据提取器
     */
    @Bean
    public MetadataExtractor metadataExtractor(DataSource dataSource) {
        return new MetadataExtractor(dataSource);
    }

    /**
     * 创建表结构比较器
     */
    @Bean
    public DbSchemaComparator dbSchemaComparator() {
        return new DbSchemaComparator();
    }

    /**
     * 创建数据检查器
     */
    @Bean
    public DataChecker dataChecker(DataSource dataSource) {
        return new DataChecker(dataSource);
    }

    /**
     * 创建表结构同步服务
     */
    @Bean
    public SchemaSyncService schemaSyncService(DataSource dataSource) {
        return new SchemaSyncService(dataSource);
    }

    /**
     * 创建数据同步服务
     */
    @Bean
    public DataSyncService dataSyncService(DataSource dataSource) {
        return new DataSyncService(dataSource);
    }

    /**
     * 创建注释检查器
     */
    @Bean
    public CommentChecker commentChecker() {
        return new CommentChecker();
    }
}
