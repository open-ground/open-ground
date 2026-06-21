package io.github.openground.common.dbcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;

/**
 * DbCheck 自动配置类
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

    @Bean
    @ConditionalOnMissingBean
    public DatabaseTypeDetector databaseTypeDetector() {
        return new DatabaseTypeDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public ScriptPathResolver scriptPathResolver(ResourceLoader resourceLoader) {
        return new ScriptPathResolver(dbCheckProperties, resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlScriptScanner sqlScriptScanner(ScriptPathResolver scriptPathResolver) {
        return new SqlScriptScanner(scriptPathResolver, dbCheckProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpleSqlParser simpleSqlParser() {
        return new SimpleSqlParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataExtractor metadataExtractor(DatabaseTypeDetector databaseTypeDetector) {
        return new MetadataExtractor(databaseTypeDetector);
    }

    @Bean
    @ConditionalOnMissingBean
    public DbSchemaComparator dbSchemaComparator() {
        return new DbSchemaComparator();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataChecker dataChecker(MetadataExtractor metadataExtractor, DatabaseTypeDetector databaseTypeDetector) {
        return new DataChecker(metadataExtractor, databaseTypeDetector, dbCheckProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSyncService dataSyncService() {
        return new DataSyncService();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommentChecker commentChecker(MetadataExtractor metadataExtractor, DatabaseTypeDetector databaseTypeDetector) {
        return new CommentChecker(metadataExtractor, databaseTypeDetector);
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaSyncService schemaSyncService() {
        return new SchemaSyncService();
    }

    @Bean
    @ConditionalOnMissingBean
    public DbCheckUtils dbCheckUtils() {
        return new DbCheckUtils();
    }

    @Bean
    @ConditionalOnMissingBean
    public DbCheckService dbCheckService(
            DatabaseTypeDetector databaseTypeDetector,
            ScriptPathResolver scriptPathResolver,
            SqlScriptScanner sqlScriptScanner,
            SimpleSqlParser simpleSqlParser,
            MetadataExtractor metadataExtractor,
            DbSchemaComparator dbSchemaComparator,
            DataChecker dataChecker,
            SchemaSyncService schemaSyncService,
            DbCheckUtils dbCheckUtils) {
        return new DbCheckService(dbCheckProperties, databaseTypeDetector,
                scriptPathResolver, sqlScriptScanner, simpleSqlParser,
                metadataExtractor, dbSchemaComparator, dataChecker,
                schemaSyncService, dbCheckUtils);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncCheckService asyncCheckService(DbCheckService dbCheckService) {
        return new AsyncCheckService(dbCheckService);
    }
}
