package io.github.openground.test.security;

import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenStore 集成测试
 * <p>验证会话的增删改查、过期清理等存储操作</p>
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("TokenStore 测试")
class TokenStoreTest {

    @Autowired
    private TokenStore tokenStore;

    private SessionEntity testSession;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        tokenStore.deleteByUsername("store_test_user");

        testSession = new SessionEntity();
        testSession.setId("test-session-001");
        testSession.setToken("test-token-001");
        testSession.setUsername("store_test_user");
        testSession.setGrantType("password");
        testSession.setSessionData("{\"username\":\"store_test_user\"}");
        testSession.setCreateTime(new Date());
        testSession.setLastAccessTime(new Date());
        testSession.setExpireTime(20991231235959L); // 遥远的未来
        testSession.setHost("127.0.0.1");
    }

    @Nested
    @DisplayName("会话 CRUD")
    class Crud {

        @Test
        @DisplayName("保存并查询会话")
        void shouldSaveAndFind() {
            tokenStore.save(testSession);

            SessionEntity found = tokenStore.findById("test-session-001");
            assertNotNull(found, "应能查询到保存的会话");
            assertEquals("store_test_user", found.getUsername());
            assertEquals("password", found.getGrantType());
        }

        @Test
        @DisplayName("更新会话")
        void shouldUpdateSession() {
            tokenStore.save(testSession);

            testSession.setLastAccessTime(new Date());
            testSession.setSessionData("{\"username\":\"store_test_user\",\"updated\":true}");
            tokenStore.update(testSession);

            SessionEntity updated = tokenStore.findById("test-session-001");
            assertNotNull(updated);
            assertTrue(updated.getSessionData().contains("updated"));
        }

        @Test
        @DisplayName("删除会话")
        void shouldDeleteSession() {
            tokenStore.save(testSession);
            tokenStore.deleteById("test-session-001");

            SessionEntity found = tokenStore.findById("test-session-001");
            assertNull(found, "删除后应查不到会话");
        }

        @Test
        @DisplayName("按用户名查询会话列表")
        void shouldFindByUsername() {
            tokenStore.save(testSession);

            // 创建第二个会话
            SessionEntity session2 = new SessionEntity();
            session2.setId("test-session-002");
            session2.setToken("test-token-002");
            session2.setUsername("store_test_user");
            session2.setGrantType("sso");
            session2.setSessionData("{}");
            session2.setCreateTime(new Date());
            session2.setLastAccessTime(new Date());
            session2.setExpireTime(20991231235959L);
            tokenStore.save(session2);

            List<SessionEntity> sessions = tokenStore.findByUsername("store_test_user");
            assertEquals(2, sessions.size(), "应查询到 2 个会话");
        }

        @Test
        @DisplayName("按用户名删除所有会话")
        void shouldDeleteByUsername() {
            tokenStore.save(testSession);
            tokenStore.deleteByUsername("store_test_user");

            List<SessionEntity> sessions = tokenStore.findByUsername("store_test_user");
            assertTrue(sessions.isEmpty(), "所有会话应被清除");
        }
    }

    @Nested
    @DisplayName("过期会话清理")
    class CleanExpired {

        @Test
        @DisplayName("清理过期会话")
        void shouldCleanExpiredSessions() {
            // 创建已过期的会话
            SessionEntity expiredSession = new SessionEntity();
            expiredSession.setId("expired-session");
            expiredSession.setToken("expired-token");
            expiredSession.setUsername("store_test_user");
            expiredSession.setGrantType("password");
            expiredSession.setSessionData("{}");
            expiredSession.setCreateTime(new Date());
            expiredSession.setLastAccessTime(new Date());
            expiredSession.setExpireTime(20200101000000L); // 已过期的时间
            tokenStore.save(expiredSession);

            // 清理过期会话
            tokenStore.cleanExpired();

            SessionEntity found = tokenStore.findById("expired-session");
            assertNull(found, "过期会话应被清理");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("查询不存在的会话应返回 null")
        void shouldReturnNullForNonExistent() {
            assertNull(tokenStore.findById("non-existent-id"));
        }

        @Test
        @DisplayName("查询不存在用户应返回空列表")
        void shouldReturnEmptyForNonExistentUser() {
            List<SessionEntity> sessions = tokenStore.findByUsername("non_existent_user");
            assertNotNull(sessions);
            assertTrue(sessions.isEmpty());
        }
    }
}
