package io.github.openground.common.jdbc;

import java.util.List;

/**
 * 数据源提供者 SPI
 *
 * <p>不同模块实现此接口，提供各自管理的数据源信息。
 * {@link DynamicDataSourceManager} 通过 {@link DataSourceProviderRegistry} 收集所有 Provider，
 * 统一管理连接池。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@link ConfigDataSourceProvider} — 从 ground.dblist 配置读取（order=10）</li>
 *   <li>SysDatasourceProvider — 从 sys_datasource 表读取（order=20，表配置优先）</li>
 * </ul>
 *
 * @author open-ground
 * @since 1.0.2
 */
public interface DataSourceProvider {

    /**
     * 获取此 Provider 管理的数据源列表
     *
     * @return 数据源描述符列表
     */
    List<DataSourceDescriptor> listDataSources();

    /**
     * 获取数据源优先级
     *
     * <p>多个 Provider 提供同名数据源时，高优先级覆盖低优先级。
     * 默认 0，dblist 配置式默认 10，sys_datasource 表式默认 20。
     *
     * @return 优先级（数值越大优先级越高）
     */
    default int getOrder() {
        return 0;
    }
}
