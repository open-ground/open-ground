package io.github.openground.common.jdbc;

import io.github.openground.common.jdbc.config.DynamicDataSourceProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 配置式数据源提供者
 *
 * <p>从 {@code ground.dblist} 配置读取数据源信息，转换为 {@link DataSourceDescriptor}。
 * 优先级 order=10，低于 sys_datasource 表式 Provider（order=20）。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
public class ConfigDataSourceProvider implements DataSourceProvider {

    private final DynamicDataSourceProperties properties;

    public ConfigDataSourceProvider(DynamicDataSourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DataSourceDescriptor> listDataSources() {
        if (properties.getDblist() == null || properties.getDblist().isEmpty()) {
            return Collections.emptyList();
        }
        return properties.getDblist().stream()
                .map(entry -> DataSourceDescriptor.builder()
                        .dsName(entry.getDsName())
                        .dbName(entry.getDbName())
                        .app(entry.getApp())
                        .url(entry.getUrl())
                        .username(entry.getUsername())
                        .password(entry.getPassword())
                        .driverClassName(entry.getDriverClassName())
                        .dbType(entry.getDbType())
                        .source("config")
                        .rawEntity(entry)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
