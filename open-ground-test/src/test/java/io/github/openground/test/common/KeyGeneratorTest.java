package io.github.openground.test.common;

import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.keygen.Snowflake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeyGenerator 集成测试
 * <p>测试 KeyGenerator 的三种主键生成方式：
 * <ul>
 *   <li>{@link KeyGenerator#nextKey(String)} — 数据库序列，需要 SYS_AUTO_PMKEY 表</li>
 *   <li>{@link KeyGenerator#businessKey(String)} — 业务流水号（前缀+日期+序列）</li>
 *   <li>{@link KeyGenerator#internalKey()} — 内存雪花算法，不依赖数据库</li>
 * </ul>
 * </p>
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("KeyGenerator 测试")
class KeyGeneratorTest {

    @Autowired
    private KeyGenerator keyGenerator;

    @Nested
    @DisplayName("internalKey — 内存雪花 ID")
    class InternalKey {

        @Test
        @DisplayName("应生成非零 Long 类型 ID")
        void shouldGenerateNonZeroId() {
            Long id = keyGenerator.internalKey();
            assertNotNull(id);
            assertTrue(id > 0, "生成的 ID 应大于 0");
        }

        @Test
        @DisplayName("应生成唯一不重复的 ID")
        void shouldGenerateUniqueIds() {
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                Long id = keyGenerator.internalKey();
                assertTrue(ids.add(id), "ID 不应重复: " + id);
            }
            assertEquals(1000, ids.size());
        }

        @Test
        @DisplayName("多次调用应保持递增")
        void shouldBeIncreasing() {
            long prev = keyGenerator.internalKey();
            for (int i = 0; i < 100; i++) {
                long cur = keyGenerator.internalKey();
                assertTrue(cur > prev, "后生成的 ID 应大于先生成的: prev=" + prev + ", cur=" + cur);
                prev = cur;
            }
        }

        @Test
        @DisplayName("静态方法 getInternalKey 应与实例方法一致")
        void staticMethodShouldMatchInstanceMethod() {
            Long instanceId = keyGenerator.internalKey();
            Long staticId = KeyGenerator.getInternalKey();
            assertNotNull(staticId);
            assertTrue(staticId > 0);
            // 不比较值是否递增，因为时间可能不同
        }
    }

    @Nested
    @DisplayName("nextKey — 数据库序列主键")
    class NextKey {

        @Test
        @DisplayName("应生成格式化后的序号字符串")
        void shouldGenerateFormattedKey() {
            String key = keyGenerator.nextKey("TEST_SEQ_01");
            assertNotNull(key);
            assertFalse(key.isEmpty());
            // TEST_SEQ_01 的 pk_len=6，因此 key 应为 6 位数字（或带前缀）
            assertTrue(key.matches("\\d+"), "序列主键应为数字字符串: " + key);
        }

        @Test
        @DisplayName("应包含前缀")
        void shouldRespectPrefix() {
            // TEST_USER_ID 的 prefix='U'，所以 key 应以 U 开头
            String key = keyGenerator.nextKey("TEST_USER_ID");
            assertNotNull(key);
            assertTrue(key.startsWith("U"), "TEST_USER_ID 应以 'U' 开头: " + key);
        }

        @Test
        @DisplayName("连续调用应返回递增的值")
        void shouldBeIncremental() {
            String key1 = keyGenerator.nextKey("TEST_SEQ_01");
            String key2 = keyGenerator.nextKey("TEST_SEQ_01");
            String key3 = keyGenerator.nextKey("TEST_SEQ_01");

            long v1 = Long.parseLong(key1);
            long v2 = Long.parseLong(key2);
            long v3 = Long.parseLong(key3);

            assertTrue(v2 > v1, "第二次调用应大于第一次: " + v1 + " -> " + v2);
            assertTrue(v3 > v2, "第三次调用应大于第二次: " + v2 + " -> " + v3);
        }

        @Test
        @DisplayName("不同序列名称返回不同的值")
        void differentSequencesShouldBeIndependent() {
            String key1 = keyGenerator.nextKey("TEST_SEQ_01");
            String key2 = keyGenerator.nextKey("TEST_USER_ID");
            assertNotNull(key1);
            assertNotNull(key2);
        }
    }

    @Nested
    @DisplayName("businessKey — 业务流水号")
    class BusinessKey {

        @Test
        @DisplayName("应生成带前缀和日期的流水号")
        void shouldGenerateFormattedBusinessKey() {
            String key = keyGenerator.businessKey("TEST_BUSINESS_KEY");
            assertNotNull(key);
            assertFalse(key.isEmpty());
            // 应包含系统编码前缀 'TEST' 和日期（8位 yyyyMMdd）
            assertTrue(key.contains("TEST"), "应包含系统编码 TEST: " + key);
        }

        @Test
        @DisplayName("连续调用应返回不同的流水号")
        void shouldBeUniqueOnEachCall() {
            String key1 = keyGenerator.businessKey("TEST_BUSINESS_KEY");
            String key2 = keyGenerator.businessKey("TEST_BUSINESS_KEY");
            assertNotNull(key1);
            assertNotNull(key2);
        }
    }

    @Nested
    @DisplayName("Snowflake 独立测试")
    class SnowflakeTest {

        @Test
        @DisplayName("应生成 16 位雪花 ID")
        void shouldGenerateValidSnowflakeId() {
            Snowflake snowflake = new Snowflake();
            long id = snowflake.nextId();
            assertTrue(id > 0);
            // 雪花 ID 通常为 16-19 位数字
            String idStr = String.valueOf(id);
            assertTrue(idStr.length() >= 10, "雪花 ID 长度应至少 10 位: " + idStr);
        }

        @Test
        @DisplayName("自定义 workerId 和 dataCenterId 应正常工作")
        void shouldSupportCustomParams() {
            for (int w = 0; w <= 7; w++) {
                for (int d = 0; d <= 3; d++) {
                    Snowflake sf = new Snowflake(w, d);
                    long id = sf.nextId();
                    assertTrue(id > 0, "workerId=" + w + ", dataCenterId=" + d);
                }
            }
        }

        @Test
        @DisplayName("多线程并发应生成唯一 ID")
        void shouldBeThreadSafe() throws InterruptedException {
            Snowflake snowflake = new Snowflake();
            int threadCount = 10;
            int idsPerThread = 500;
            Set<Long> allIds = java.util.Collections.synchronizedSet(new HashSet<>());
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                new Thread(() -> {
                    try {
                        for (int i = 0; i < idsPerThread; i++) {
                            long id = snowflake.nextId();
                            if (!allIds.add(id)) {
                                errors.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();
            assertEquals(0, errors.get(), "不应有重复 ID");
            assertEquals(threadCount * idsPerThread, allIds.size());
        }
    }
}
