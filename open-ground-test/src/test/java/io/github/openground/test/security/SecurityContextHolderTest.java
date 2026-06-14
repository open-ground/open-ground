package io.github.openground.test.security;

import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenManager;
import io.github.openground.common.security.spi.DefaultUserDetails;
import io.github.openground.common.security.spi.UserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityContextHolder / ThreadLocal 隔离测试
 * <p>验证会话上下文的线程隔离和生命周期管理</p>
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("SecurityContextHolder 测试")
class SecurityContextHolderTest {

    @Autowired
    private TokenManager tokenManager;

    @AfterEach
    void tearDown() {
        tokenManager.clearCurrentSession();
    }

    @Test
    @DisplayName("未设置会话时应返回 null")
    void shouldReturnNullWhenNoSession() {
        assertNull(tokenManager.getCurrentSession());
        assertNull(tokenManager.getCurrentUser());
    }

    @Test
    @DisplayName("设置会话后应能获取用户信息")
    void shouldGetUserAfterSettingSession() {
        SessionEntity session = new SessionEntity();
        session.setId("ctx-test-session");
        session.setUsername("ctx_test_user");

        UserDetails user = DefaultUserDetails.builder()
                .id(1L)
                .username("ctx_test_user")
                .realName("上下文测试用户")
                .roleIds(Arrays.asList(1L))
                .roleNames(Arrays.asList("ROLE_USER"))
                .authorities(Arrays.asList("user:read"))
                .orgId("1001")
                .orgName("测试机构")
                .corpId(1L)
                .corpName("测试法人")
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .enabled(true)
                .createTime(new Date())
                .lastLoginTime(new Date())
                .clientIp("127.0.0.1")
                .build();

        session.setSessionData(com.alibaba.fastjson.JSON.toJSONString(user));
        tokenManager.setCurrentSession(session);

        UserDetails retrieved = tokenManager.getCurrentUser();
        assertNotNull(retrieved, "应能获取当前用户");
        assertEquals("ctx_test_user", retrieved.getUsername());
        assertEquals("上下文测试用户", retrieved.getRealName());
    }

    @Test
    @DisplayName("清除后应返回 null")
    void shouldReturnNullAfterClear() {
        SessionEntity session = new SessionEntity();
        session.setId("ctx-test-session");
        session.setSessionData("{\"username\":\"test\"}");
        tokenManager.setCurrentSession(session);

        tokenManager.clearCurrentSession();

        assertNull(tokenManager.getCurrentSession());
        assertNull(tokenManager.getCurrentUser());
    }

    @Test
    @DisplayName("getCurrentUser 在 sessionData 格式错误时应返回 null")
    void shouldReturnNullForInvalidSessionData() {
        SessionEntity session = new SessionEntity();
        session.setId("ctx-test-session");
        session.setSessionData("not-valid-json{{{");
        tokenManager.setCurrentSession(session);

        UserDetails user = tokenManager.getCurrentUser();
        assertNull(user, "无效 JSON 应返回 null 而非抛异常");
    }
}
