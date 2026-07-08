package io.github.openground.common.jdbc;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源提供者注册中心
 *
 * <p>自动收集所有 {@link DataSourceProvider} 实现，按 {@link DataSourceProvider#getOrder()}
 * 从高到低排序，高优先级 Provider 的同名数据源覆盖低优先级。
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
public class DataSourceProviderRegistry {

    private final List<DataSourceProvider> providers;

    /**
     * 数据源描述符缓存（dsName → descriptor），高优先级覆盖低优先级
     */
    private final Map<String, DataSourceDescriptor> descriptorMap = new ConcurrentHashMap<>();

    /** 是否已全量加载所有 Provider 的数据源（含查库的 Provider） */
    private volatile boolean allLoaded = false;

    public DataSourceProviderRegistry(List<DataSourceProvider> providers) {
        this.providers = providers != null ? providers : Collections.emptyList();
        // 只加载不查库的 Provider（如 ConfigDataSourceProvider）
        // SysDatasourceProvider 等查库的 Provider 延迟到首次访问时加载
        loadConfigProviders();
    }

    /**
     * 只加载配置式 Provider（不查库），启动时执行
     */
    private void loadConfigProviders() {
        Map<String, DataSourceDescriptor> newMap = new LinkedHashMap<>();
        providers.stream()
                .sorted(Comparator.comparingInt(DataSourceProvider::getOrder))
                .forEach(provider -> {
                    // 只加载 source=config 的 Provider（不查库）
                    if (isConfigProvider(provider)) {
                        try {
                            List<DataSourceDescriptor> list = provider.listDataSources();
                            if (list != null) {
                                for (DataSourceDescriptor desc : list) {
                                    newMap.put(desc.getDsName(), desc);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Provider [{}] 启动加载失败: {}", provider.getClass().getSimpleName(), e.getMessage());
                        }
                    }
                });
        descriptorMap.putAll(newMap);
        log.info("数据源注册中心启动加载完成（仅配置式），共 {} 个数据源: {}", descriptorMap.size(), descriptorMap.keySet());
    }

    /**
     * 判断是否为配置式 Provider（不查库）
     */
    private boolean isConfigProvider(DataSourceProvider provider) {
        return provider instanceof ConfigDataSourceProvider;
    }

    /**
     * 刷新数据源缓存
     *
     * <p>重新从所有 Provider 收集数据源，按优先级排序后覆盖。
     */
    public synchronized void refresh() {
        Map<String, DataSourceDescriptor> newMap = new LinkedHashMap<>();
        // 按 order 从低到高排序，后面覆盖前面（高优先级覆盖低优先级）
        providers.stream()
                .sorted(Comparator.comparingInt(DataSourceProvider::getOrder))
                .forEach(provider -> {
                    try {
                        List<DataSourceDescriptor> list = provider.listDataSources();
                        if (list != null) {
                            for (DataSourceDescriptor desc : list) {
                                newMap.put(desc.getDsName(), desc);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Provider [{}] 获取数据源列表失败: {}", provider.getClass().getSimpleName(), e.getMessage());
                    }
                });
        descriptorMap.clear();
        descriptorMap.putAll(newMap);
        allLoaded = true;
        log.info("数据源注册中心刷新完成，共 {} 个数据源: {}", descriptorMap.size(), descriptorMap.keySet());
    }

    /**
     * 获取数据源描述符
     *
     * <p>缓存未命中时，逐个遍历非配置式 Provider 查找指定 dsName，找到则缓存。
     * 不触发全量加载。
     *
     * @param dsName 数据源名称
     * @return 描述符，不存在返回 null
     */
    public DataSourceDescriptor getDescriptor(String dsName) {
        DataSourceDescriptor desc = descriptorMap.get(dsName);
        if (desc != null) {
            return desc;
        }
        // 缓存未命中，逐个查找非配置式 Provider（查库的 Provider）
        return loadFromProviders(dsName);
    }

    /**
     * 从非配置式 Provider 中按 dsName 查找，找到则缓存
     *
     * @param dsName 数据源名称
     * @return 描述符，不存在返回 null
     */
    private DataSourceDescriptor loadFromProviders(String dsName) {
        // 按 order 从高到低查找（高优先级先查）
        List<DataSourceProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(DataSourceProvider::getOrder).reversed());
        for (DataSourceProvider provider : sorted) {
            // 跳过配置式 Provider（已在启动时加载）
            if (isConfigProvider(provider)) {
                continue;
            }
            try {
                List<DataSourceDescriptor> list = provider.listDataSources();
                if (list != null) {
                    for (DataSourceDescriptor d : list) {
                        // 缓存找到的所有数据源，避免下次再查
                        descriptorMap.putIfAbsent(d.getDsName(), d);
                    }
                }
            } catch (Exception e) {
                log.warn("Provider [{}] 查找数据源[{}]失败: {}", provider.getClass().getSimpleName(), dsName, e.getMessage());
            }
        }
        return descriptorMap.get(dsName);
    }

    /**
     * 获取所有数据源描述符
     *
     * <p>首次调用时触发全量加载（调用所有 Provider 的 listDataSources），
     * 将所有数据源描述符加载到缓存。后续调用直接返回缓存。
     * 启动时不查库，避免影响启动速度。
     *
     * @return 不可变的数据源描述符集合
     */
    public Collection<DataSourceDescriptor> getAllDescriptors() {
        if (!allLoaded) {
            synchronized (this) {
                if (!allLoaded) {
                    refresh();
                    allLoaded = true;
                }
            }
        }
        return Collections.unmodifiableCollection(descriptorMap.values());
    }

    /**
     * 获取所有数据源名称
     *
     * @return 数据源名称集合
     */
    public Set<String> getDataSourceNames() {
        return Collections.unmodifiableSet(descriptorMap.keySet());
    }

    /**
     * 获取默认数据源名称（第一个）
     *
     * @return 默认数据源名称，无则 null
     */
    public String getDefaultDsName() {
        return descriptorMap.keySet().stream().findFirst().orElse(null);
    }
}
