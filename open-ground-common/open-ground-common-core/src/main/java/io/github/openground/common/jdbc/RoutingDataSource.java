package io.github.openground.common.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;

/**
 * 路由数据源 - MyBatis 动态多数据源的核心
 *
 * <p>继承 Spring 的 {@link AbstractRoutingDataSource}，通过 ThreadLocal 栈
 * 传递当前数据源名称（dsName），在 {@code getConnection()} 时路由到真实数据源。
 * 这是 Spring 生态多数据源的标准实践。
 *
 * <h3>设计理念</h3>
 * <p>open-ground 的 MyBatis 动态多数据源方案不采用"每个数据源一个独立 SqlSessionFactory"
 * 的做法（该做法在 MyBatis-Plus 场景下需要重建 Configuration/GlobalConfig/TableInfo，
 * 代价极高且易遗漏插件）。而是让<b>主 SqlSessionFactory 的 Environment 持有此路由数据源</b>，
 * 运行时通过切换 ThreadLocal 中的 dsName 实现数据源路由，从而：
 * <ul>
 *   <li>完全复用主 SqlSessionFactory 的 Configuration（MappedStatement、类型别名、
 *       拦截器、MyBatis-Plus 的 TableInfo/GlobalConfig 等），零重建、零污染</li>
 *   <li>对调用方透明，{@code DynamicJdbcTemplate} 的 API 无需改动</li>
 *   <li>应用方可直接使用 push/pop 实现自定义路由场景（如 AOP + 注解）</li>
 * </ul>
 *
 * <h3>循环依赖打破</h3>
 * <p>{@code DynamicDataSourceManager} 的依赖链经过 {@code SysDatasourceMapper} 会回到主
 * {@code SqlSessionFactory}，而后者又依赖本数据源（@Primary）。为打破循环，本类
 * <b>初始化时只依赖 Spring 主数据源</b>（defaultTargetDataSource），不依赖
 * {@code DynamicDataSourceManager}；后者通过 {@link #setDataSourceManager} 在容器
 * 初始化完成后延迟注入，运行时 {@link #determineTargetDataSource()} 才使用它查找动态数据源。
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ThreadLocal} 存储数据源名称栈，线程隔离，天然线程安全。
 * 栈式设计支持嵌套调用（如 dsName=A 中调用 dsName=B，执行完毕后恢复 A）。
 * 配合 try-finally 的 pop() 保证栈平衡，避免线程池场景下的 ThreadLocal 泄漏。
 *
 * <h3>默认数据源</h3>
 * <p>当 ThreadLocal 栈为空（未 push 任何 dsName）时，{@code determineCurrentLookupKey()}
 * 返回 {@code null}，{@link #determineTargetDataSource()} 直接返回默认数据源（Spring 主数据源）。
 *
 * @author open-ground
 * @since 1.0.7
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    /**
     * 数据源名称栈（ThreadLocal，支持嵌套调用）
     */
    private static final ThreadLocal<java.util.Deque<String>> dsNameStack = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    /**
     * 动态数据源管理器（延迟注入，运行时用于按 dsName 查找真实数据源）
     */
    private DynamicDataSourceManager dataSourceManager;

    public RoutingDataSource() {
        // 空 map 占位，满足 AbstractRoutingDataSource.afterPropertiesSet() 的非空校验
        // 实际数据源查找由 determineTargetDataSource() 重写逻辑运行时完成
        setTargetDataSources(new HashMap<>());
    }

    /**
     * 设置动态数据源管理器（延迟注入）
     *
     * <p>由 {@code DynamicDataSourceAutoConfiguration} 在容器初始化完成后调用，
     * 打破 {@code RoutingDataSource} -> {@code DynamicDataSourceManager} ->
     * {@code SysDatasourceMapper} -> {@code SqlSessionFactory} -> {@code RoutingDataSource} 的循环依赖。
     *
     * @param dataSourceManager 动态数据源管理器
     */
    public void setDataSourceManager(DynamicDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        log.info("RoutingDataSource 已绑定 DynamicDataSourceManager，动态数据源路由就绪");
    }

    /**
     * 返回当前线程的数据源名称（栈顶），未设置时返回 null（回退到默认数据源）
     *
     * @return 当前数据源名称，或 null
     */
    @Override
    protected Object determineCurrentLookupKey() {
        java.util.Deque<String> stack = dsNameStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 重写目标数据源查找，支持运行时动态数据源懒加载
     *
     * <p>默认实现从 {@code resolvedDataSources} 静态 map 查找，但 open-ground 的动态数据源
     * 是懒加载的（运行时按需创建 Druid 连接池），无法在初始化时枚举。本重写改为：
     * <ul>
     *   <li>栈空（无 dsName）- 返回默认数据源（Spring 主数据源）</li>
     *   <li>栈非空 - 通过 {@code DynamicDataSourceManager.getDataSource(dsName)} 运行时查找</li>
     * </ul>
     *
     * @return 目标数据源
     */
    @Override
    protected DataSource determineTargetDataSource() {
        String dsName = (String) determineCurrentLookupKey();
        if (dsName == null) {
            // 无 dsName，回退默认数据源
            return getDefaultDataSource();
        }
        if (dataSourceManager == null) {
            log.warn("DynamicDataSourceManager 未注入，dsName=[{}] 回退默认数据源", dsName);
            return getDefaultDataSource();
        }
        return dataSourceManager.getDataSource(dsName);
    }

    /**
     * 获取默认数据源
     *
     * <p>优先用 {@code resolvedDefaultDataSource}（初始化时设置的默认数据源），
     * 若未设置则通过 {@code DynamicDataSourceManager.getDefaultDataSource()} 运行时获取。
     *
     * @return 默认数据源
     */
    private DataSource getDefaultDataSource() {
        DataSource defaultDs = getResolvedDefaultDataSource();
        if (defaultDs != null) {
            return defaultDs;
        }
        if (dataSourceManager != null) {
            return dataSourceManager.getDefaultDataSource();
        }
        throw new IllegalStateException("无可用默认数据源：resolvedDefaultDataSource 未设置且 DynamicDataSourceManager 未注入");
    }

    /**
     * 压入当前数据源名称（进入数据源上下文）
     *
     * <p>栈式设计，支持嵌套调用。必须与 {@link #pop()} 成对使用（try-finally）。
     *
     * <pre>
     * routingDataSource.push("business_db");
     * try {
     *     // 此范围内的所有 SQL 操作走 business_db
     * } finally {
     *     routingDataSource.pop();
     * }
     * </pre>
     *
     * @param dsName 数据源名称
     */
    public void push(String dsName) {
        dsNameStack.get().push(dsName);
    }

    /**
     * 弹出当前数据源名称（退出数据源上下文）
     *
     * <p>必须与 {@link #push(String)} 成对使用。在 finally 块中调用以保证栈平衡。
     */
    public void pop() {
        java.util.Deque<String> stack = dsNameStack.get();
        stack.pop();
        if (stack.isEmpty()) {
            dsNameStack.remove();
        }
    }

    /**
     * 清除当前线程的数据源上下文
     *
     * <p>防御性方法，用于清理可能残留的 ThreadLocal。正常使用 push/pop 无需调用。
     */
    public static void clear() {
        dsNameStack.remove();
    }
}
