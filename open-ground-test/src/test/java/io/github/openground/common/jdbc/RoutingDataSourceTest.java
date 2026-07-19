package io.github.openground.common.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.druid.pool.DruidDataSource;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RoutingDataSource 单元测试
 *
 * <p>验证路由数据源的核心逻辑：push/pop 栈式路由、默认数据源回退、
 * 嵌套调用、DynamicDataSourceManager 延迟注入、determineTargetDataSource 动态查找。
 *
 * <p>纯单元测试，不依赖 Spring 容器和数据库，使用 Mockito mock DataSource。
 *
 * @author open-ground
 * @since 1.0.7
 */
@DisplayName("RoutingDataSource 路由数据源测试")
class RoutingDataSourceTest {

    private RoutingDataSource routingDataSource;
    private DruidDataSource defaultDataSource;
    private DruidDataSource dynamicDataSourceA;
    private DruidDataSource dynamicDataSourceB;
    private DynamicDataSourceManager dataSourceManager;

    @BeforeEach
    void setUp() {
        routingDataSource = new RoutingDataSource();
        defaultDataSource = mock(DruidDataSource.class);
        dynamicDataSourceA = mock(DruidDataSource.class);
        dynamicDataSourceB = mock(DruidDataSource.class);
        dataSourceManager = mock(DynamicDataSourceManager.class);

        // 设置默认数据源（模拟 @Qualifier("dataSource") 注入的业务数据源）
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        // 触发 afterPropertiesSet 初始化 resolvedDefaultDataSource
        try {
            routingDataSource.afterPropertiesSet();
        } catch (Exception e) {
            fail("afterPropertiesSet 不应抛异常", e);
        }

        // mock DynamicDataSourceManager 的行为
        // 用 doReturn 绕过返回类型 DruidDataSource vs DataSource 的泛型检查
        doReturn(dynamicDataSourceA).when(dataSourceManager).getDataSource("db_a");
        doReturn(dynamicDataSourceB).when(dataSourceManager).getDataSource("db_b");
    }

    @AfterEach
    void tearDown() {
        // 清理 ThreadLocal，避免跨测试污染
        RoutingDataSource.clear();
    }

    @Nested
    @DisplayName("默认数据源路由")
    class DefaultDataSourceRouting {

        @Test
        @DisplayName("未 push dsName 时返回默认数据源")
        void shouldReturnDefaultDataSourceWhenNoPush() {
            DataSource result = routingDataSource.determineTargetDataSource();
            assertSame(defaultDataSource, result,
                    "未 push 时应返回默认数据源（Spring 主数据源）");
        }

        @Test
        @DisplayName("push 后 pop 恢复为默认数据源")
        void shouldReturnDefaultAfterPop() {
            routingDataSource.setDataSourceManager(dataSourceManager);

            routingDataSource.push("db_a");
            DataSource routed = routingDataSource.determineTargetDataSource();
            assertSame(dynamicDataSourceA, routed, "push 后应返回动态数据源");

            routingDataSource.pop();
            DataSource afterPop = routingDataSource.determineTargetDataSource();
            assertSame(defaultDataSource, afterPop, "pop 后应恢复默认数据源");
        }
    }

    @Nested
    @DisplayName("动态数据源路由")
    class DynamicDataSourceRouting {

        @Test
        @DisplayName("push db_a 后路由到 db_a 的数据源")
        void shouldRouteToDynamicDataSource() {
            routingDataSource.setDataSourceManager(dataSourceManager);

            routingDataSource.push("db_a");
            DataSource result = routingDataSource.determineTargetDataSource();

            assertSame(dynamicDataSourceA, result, "应路由到 db_a 数据源");
            verify(dataSourceManager).getDataSource("db_a");
        }

        @Test
        @DisplayName("push db_b 后路由到 db_b 的数据源")
        void shouldRouteToDifferentDataSource() {
            routingDataSource.setDataSourceManager(dataSourceManager);

            routingDataSource.push("db_b");
            DataSource result = routingDataSource.determineTargetDataSource();

            assertSame(dynamicDataSourceB, result, "应路由到 db_b 数据源");
        }
    }

    @Nested
    @DisplayName("嵌套调用")
    class NestedCall {

        @Test
        @DisplayName("嵌套 push/pop 支持数据源切换与恢复")
        void shouldSupportNestedPushPop() {
            routingDataSource.setDataSourceManager(dataSourceManager);

            // 第一层：db_a
            routingDataSource.push("db_a");
            assertSame(dynamicDataSourceA, routingDataSource.determineTargetDataSource(),
                    "第一层应路由到 db_a");

            // 第二层：db_b（嵌套）
            routingDataSource.push("db_b");
            assertSame(dynamicDataSourceB, routingDataSource.determineTargetDataSource(),
                    "第二层应路由到 db_b");

            // 退出第二层，恢复 db_a
            routingDataSource.pop();
            assertSame(dynamicDataSourceA, routingDataSource.determineTargetDataSource(),
                    "退出第二层后应恢复 db_a");

            // 退出第一层，恢复默认
            routingDataSource.pop();
            assertSame(defaultDataSource, routingDataSource.determineTargetDataSource(),
                    "退出第一层后应恢复默认数据源");
        }
    }

    @Nested
    @DisplayName("DynamicDataSourceManager 延迟注入")
    class DelayedInjection {

        @Test
        @DisplayName("dataSourceManager 未注入时 push dsName 回退默认数据源")
        void shouldFallbackToDefaultWhenManagerNotInjected() {
            // 未调用 setDataSourceManager
            routingDataSource.push("db_a");
            DataSource result = routingDataSource.determineTargetDataSource();

            assertSame(defaultDataSource, result,
                    "dataSourceManager 未注入时应回退默认数据源");
        }

        @Test
        @DisplayName("setDataSourceManager 后路由生效")
        void shouldRouteAfterManagerInjected() {
            // 先 push（此时未注入，回退默认）
            routingDataSource.push("db_a");
            assertSame(defaultDataSource, routingDataSource.determineTargetDataSource(),
                    "注入前应回退默认");

            // 注入后路由生效
            routingDataSource.setDataSourceManager(dataSourceManager);
            assertSame(dynamicDataSourceA, routingDataSource.determineTargetDataSource(),
                    "注入后应路由到 db_a");
        }
    }

    @Nested
    @DisplayName("ThreadLocal 生命周期")
    class ThreadLocalLifecycle {

        @Test
        @DisplayName("pop 后栈空时 ThreadLocal 被清理")
        void shouldCleanThreadLocalWhenStackEmpty() {
            routingDataSource.push("db_a");
            assertFalse(getDsNameStack().isEmpty(), "push 后栈应非空");

            routingDataSource.pop();
            // pop 后栈空，ThreadLocal 应被 remove（getDsNameStack 返回新的空栈）
            // 验证方式：determineCurrentLookupKey 返回 null
            assertNull(routingDataSource.determineCurrentLookupKey(),
                    "pop 后栈空，lookupKey 应为 null");
        }

        @Test
        @DisplayName("clear 静态方法清理 ThreadLocal")
        void shouldClearThreadLocal() {
            routingDataSource.push("db_a");
            routingDataSource.push("db_b");

            RoutingDataSource.clear();

            assertNull(routingDataSource.determineCurrentLookupKey(),
                    "clear 后 lookupKey 应为 null");
        }

        @Test
        @DisplayName("多线程隔离 - 不同线程路由互不影响")
        void shouldIsolateBetweenThreads() throws InterruptedException {
            routingDataSource.setDataSourceManager(dataSourceManager);

            routingDataSource.push("db_a");
            assertSame(dynamicDataSourceA, routingDataSource.determineTargetDataSource(),
                    "主线程应路由到 db_a");

            Thread thread = new Thread(() -> {
                // 子线程栈应为空
                assertNull(routingDataSource.determineCurrentLookupKey(),
                        "子线程 lookupKey 应为 null（线程隔离）");
                assertSame(defaultDataSource, routingDataSource.determineTargetDataSource(),
                        "子线程应返回默认数据源");
            });
            thread.start();
            thread.join(5000);

            // 主线程仍为 db_a
            assertSame(dynamicDataSourceA, routingDataSource.determineTargetDataSource(),
                    "子线程执行后主线程仍应路由到 db_a");
        }

        private java.util.Deque<String> getDsNameStack() {
            ThreadLocal<java.util.Deque<String>> tl = (ThreadLocal<java.util.Deque<String>>)
                    ReflectionTestUtils.getField(RoutingDataSource.class, "dsNameStack");
            return tl.get();
        }
    }

    @Nested
    @DisplayName("初始化与默认数据源兜底")
    class Initialization {

        @Test
        @DisplayName("构造函数设置空 targetDataSources 满足 afterPropertiesSet 校验")
        void shouldInitWithEmptyTargetDataSources() {
            // 验证构造函数不会因 targetDataSources 为 null 而失败
            assertDoesNotThrow(() -> {
                RoutingDataSource ds = new RoutingDataSource();
                ds.setDefaultTargetDataSource(defaultDataSource);
                ds.afterPropertiesSet();
            }, "构造 + afterPropertiesSet 不应抛异常");
        }

        @Test
        @DisplayName("resolvedDefaultDataSource 未设置且 dataSourceManager 未注入时抛异常")
        void shouldThrowWhenNoDefaultAvailable() {
            RoutingDataSource ds = new RoutingDataSource();
            // 不设 defaultTargetDataSource，不设 dataSourceManager
            ds.afterPropertiesSet();
            // 此时 resolvedDefaultDataSource 为 null
            assertThrows(IllegalStateException.class, ds::determineTargetDataSource,
                    "无默认数据源时应抛 IllegalStateException");
        }

        @Test
        @DisplayName("resolvedDefaultDataSource 未设置但 dataSourceManager 已注入时用 manager 兜底")
        void shouldFallbackToManagerWhenNoResolvedDefault() {
            RoutingDataSource ds = new RoutingDataSource();
            // 不设 defaultTargetDataSource
            ds.afterPropertiesSet();
            ds.setDataSourceManager(dataSourceManager);

            doReturn(defaultDataSource).when(dataSourceManager).getDefaultDataSource();

            DataSource result = ds.determineTargetDataSource();
            assertSame(defaultDataSource, result,
                    "resolvedDefaultDataSource 未设置时应通过 dataSourceManager.getDefaultDataSource() 兜底");
            verify(dataSourceManager).getDefaultDataSource();
        }
    }
}
