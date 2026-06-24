package io.github.openground.test.security;

import io.github.openground.base.exception.CommonException;
import io.github.openground.common.security.ApiKeyDO;
import io.github.openground.common.security.ApiKeyResponseVO;
import io.github.openground.common.security.ApiKeyService;
import io.github.openground.common.security.TokenManager;
import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyService 集成测试
 * <p>验证 API Key 的生成、校验、撤销等核心功能</p>
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ApiKeyService 测试")
class ApiKeyServiceTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private TokenStore tokenStore;

    private static final String TEST_USER_ID = "apikey_test_user_id";
    private static final String TEST_USERNAME = "apikey_test_user";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        List<ApiKeyResponseVO> existingKeys = apiKeyService.getUserKeyList(TEST_USER_ID);
        for (ApiKeyResponseVO key : existingKeys) {
            apiKeyService.revokeKey(key.getId());
        }
    }

    @Nested
    @DisplayName("API Key 生成")
    class Generate {

        @Test
        @DisplayName("应成功生成 API Key")
        void shouldGenerateApiKey() {
            Long expireTime = futureTime(30);
            ApiKeyResponseVO vo = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "测试 Key", expireTime);

            assertNotNull(vo, "生成的 Key 不应为空");
            assertNotNull(vo.getApiKey(), "Key 值不应为空");
            assertTrue(vo.getApiKey().startsWith("sk-"), "Key 应以 sk- 开头");
            assertEquals("测试 Key", vo.getName());
            assertEquals("0", vo.getStatus(), "状态应为启用");
        }

        @Test
        @DisplayName("生成的 Key 应唯一")
        void shouldGenerateUniqueKeys() {
            Long expireTime = futureTime(30);
            ApiKeyResponseVO vo1 = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "Key1", expireTime);
            ApiKeyResponseVO vo2 = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "Key2", expireTime);

            assertNotEquals(vo1.getApiKey(), vo2.getApiKey(), "两次生成的 Key 应不同");
        }
    }

    @Nested
    @DisplayName("API Key 校验")
    class Validate {

        @Test
        @DisplayName("有效 Key 应校验通过")
        void shouldValidateValidKey() {
            Long expireTime = futureTime(30);
            ApiKeyResponseVO vo = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "测试", expireTime);

            ApiKeyDO validated = apiKeyService.validateKey(vo.getApiKey());
            assertNotNull(validated, "有效 Key 应校验通过");
            assertEquals(TEST_USERNAME, validated.getUsername());
        }

        @Test
        @DisplayName("无效格式的 Key 应抛异常")
        void shouldRejectInvalidFormat() {
            assertThrows(CommonException.class, () -> apiKeyService.validateKey("invalid-key-format"));
            assertThrows(CommonException.class, () -> apiKeyService.validateKey(null));
            assertThrows(CommonException.class, () -> apiKeyService.validateKey(""));
        }

        @Test
        @DisplayName("不存在的 Key 应抛异常")
        void shouldRejectNonExistentKey() {
            assertThrows(CommonException.class, () -> apiKeyService.validateKey("sk-00000000000000000000000000000000"));
        }

        @Test
        @DisplayName("过期的 Key 应抛异常")
        void shouldRejectExpiredKey() {
            Long pastTime = pastTime(1);
            ApiKeyResponseVO vo = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "过期 Key", pastTime);

            assertThrows(CommonException.class, () -> apiKeyService.validateKey(vo.getApiKey()));
        }
    }

    @Nested
    @DisplayName("API Key 撤销")
    class Revoke {

        @Test
        @DisplayName("撤销后 Key 应不可用")
        void shouldRevokeKey() {
            Long expireTime = futureTime(30);
            ApiKeyResponseVO vo = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "待撤销", expireTime);

            apiKeyService.revokeKey(vo.getId());

            // 撤销后查询列表应为空
            List<ApiKeyResponseVO> keys = apiKeyService.getUserKeyList(TEST_USER_ID);
            assertTrue(keys.isEmpty(), "撤销后列表应为空");
        }
    }

    @Nested
    @DisplayName("API Key 列表查询")
    class ListKeys {

        @Test
        @DisplayName("应返回用户的所有 Key")
        void shouldReturnAllUserKeys() {
            Long expireTime = futureTime(30);
            apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "Key1", expireTime);
            apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "Key2", expireTime);

            List<ApiKeyResponseVO> keys = apiKeyService.getUserKeyList(TEST_USER_ID);
            assertEquals(2, keys.size(), "应返回 2 个 Key");
        }

        @Test
        @DisplayName("无 Key 的用户应返回空列表")
        void shouldReturnEmptyForNoKeys() {
            List<ApiKeyResponseVO> keys = apiKeyService.getUserKeyList("no_key_user");
            assertNotNull(keys);
            assertTrue(keys.isEmpty());
        }
    }

    @Nested
    @DisplayName("TokenManager.validateApiKey")
    class TokenManagerApiKey {

        @Test
        @DisplayName("有效 API Key 应设置当前会话")
        void shouldSetSessionForValidApiKey() {
            Long expireTime = futureTime(30);
            ApiKeyResponseVO vo = apiKeyService.generateKey(TEST_USER_ID, TEST_USERNAME, "会话测试", expireTime);

            SessionEntity session = tokenManager.validateApiKey(vo.getApiKey());
            assertNotNull(session, "应返回会话");
            assertEquals(TEST_USERNAME, session.getUsername());
            assertEquals("api_key", session.getGrantType());

            // 验证 ThreadLocal 已设置
            assertNotNull(tokenManager.getCurrentSession());

            tokenManager.clearCurrentSession();
        }
    }

    // ==================== 辅助方法 ====================

    private Long futureTime(int days) {
        return System.currentTimeMillis() + days * 24L * 60 * 60 * 1000;
    }

    private Long pastTime(int days) {
        return System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
    }
}
