package io.github.openground.test.common.auth;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.cloud.auth.FeignAuthCommonProvider;
import io.github.openground.common.auth.AuthCommonProvider;
import io.github.openground.common.auth.LocalAuthCommonProvider;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuthCommonProvider 集成测试
 * <p>
 * 测试 LocalAuthCommonProvider（直连数据库）和 FeignAuthCommonProvider（Feign 远程调用）两种实现。
 * </p>
 *
 * @author open-ground
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("AuthCommonProvider 测试")
class AuthCommonProviderTest {

    @Autowired
    private DynamicJdbcTemplate dynamicJdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate primaryJdbcTemplate;

    /**
     * JDK 8 兼容的 Map.of 替代：键值交替传入，键必须为 String
     */
    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((String) kvs[i], kvs[i + 1]);
        }
        return map;
    }

    @Nested
    @DisplayName("LocalAuthCommonProvider — 直连数据库")
    class LocalAuthProviderTest {

        @Test
        @DisplayName("getuserFuncs: 应返回用户功能权限")
        void shouldGetUserFuncs() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getuserFuncs("zhangqsg");
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(Map.class, result.getData());
        }

        @Test
        @DisplayName("getuserFuncs: 用户不存在应返回空 Map")
        void shouldGetUserFuncsWhenUserNotFound() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getuserFuncs("nonexistent_user");
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(Map.class, result.getData());
            assertTrue(((Map<?, ?>) result.getData()).isEmpty());
        }

        @Test
        @DisplayName("getUserInfoByUserName: 应返回用户信息含 deptName 和 roleIds")
        void shouldGetUserInfoByUserName() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getUserInfoByUserName("zhangqsg");
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(Map.class, result.getData());
            Map<?, ?> userInfo = (Map<?, ?>) result.getData();
            assertTrue(userInfo.containsKey("userId"));
            assertTrue(userInfo.containsKey("userName"));
            assertTrue(userInfo.containsKey("deptName"));
            assertTrue(userInfo.containsKey("roleIds"));
        }

        @Test
        @DisplayName("getUserInfoByUserName: 用户不存在应返回空 Map")
        void shouldGetUserInfoByUserNameWhenNotFound() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getUserInfoByUserName("nonexistent_user");
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(Map.class, result.getData());
            assertTrue(((Map<?, ?>) result.getData()).isEmpty());
        }

        @Test
        @DisplayName("getUsersByUserNames: 应按 user_name 批量查询用户")
        void shouldGetUsersByUserNames() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getUsersByUserNames(Collections.singletonList("zhangqsg"));
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(List.class, result.getData());
        }

        @Test
        @DisplayName("getUsersByUserNames: 空列表应返回空 List")
        void shouldGetUsersByUserNamesWhenEmpty() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getUsersByUserNames(Collections.emptyList());
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(List.class, result.getData());
            assertTrue(((List<?>) result.getData()).isEmpty());
        }

        @Test
        @DisplayName("getDictByType: 应返回字典列表")
        void shouldGetDictByType() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.getDictByType("YOrN", "all");
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(List.class, result.getData());
        }

        @Test
        @DisplayName("listAllOrg: 应返回机构列表")
        void shouldListAllOrg() {
            AuthCommonProvider provider = new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
            CommonResult<?> result = provider.listAllOrg();
            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            assertInstanceOf(List.class, result.getData());
        }
    }

    @Nested
    @DisplayName("FeignAuthCommonProvider — Feign 远程调用")
    class FeignAuthProviderTest {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("getuserFuncs: 应委托 FeignClient 调用")
        void shouldDelegateGetUserFuncs() {
            AuthFeignClient feignClient = mock(AuthFeignClient.class);
            CommonResult<?> mockResult = CommonResult.success(mapOf("FUN001", "权限A"));
            doReturn(mockResult).when(feignClient).getuserFuncs("zhangqsg");

            AuthCommonProvider provider = new FeignAuthCommonProvider(feignClient);
            CommonResult<?> result = provider.getuserFuncs("zhangqsg");

            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            verify(feignClient).getuserFuncs("zhangqsg");
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("getUserInfoByUserName: 应委托 FeignClient 调用")
        void shouldDelegateGetUserInfoByUserName() {
            AuthFeignClient feignClient = mock(AuthFeignClient.class);
            CommonResult<?> mockResult = CommonResult.success(mapOf("userId", 1L, "userName", "zhangqsg"));
            doReturn(mockResult).when(feignClient).getUserInfoByUserName("zhangqsg");

            AuthCommonProvider provider = new FeignAuthCommonProvider(feignClient);
            CommonResult<?> result = provider.getUserInfoByUserName("zhangqsg");

            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            verify(feignClient).getUserInfoByUserName("zhangqsg");
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("getUsersByUserNames: 应委托 FeignClient 调用")
        void shouldDelegateGetUsersByUserNames() {
            AuthFeignClient feignClient = mock(AuthFeignClient.class);
            CommonResult<?> mockResult = CommonResult.success(Collections.singletonList(mapOf("userId", 1L, "userName", "zhangqsg")));
            doReturn(mockResult).when(feignClient).getUsersByUserNames(Collections.singletonList("zhangqsg"));

            AuthCommonProvider provider = new FeignAuthCommonProvider(feignClient);
            CommonResult<?> result = provider.getUsersByUserNames(Collections.singletonList("zhangqsg"));

            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            verify(feignClient).getUsersByUserNames(Collections.singletonList("zhangqsg"));
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("getDictByType: 应委托 FeignClient 调用")
        void shouldDelegateGetDictByType() {
            AuthFeignClient feignClient = mock(AuthFeignClient.class);
            CommonResult<?> mockResult = CommonResult.success(Collections.singletonList(mapOf("dictName", "是", "dictValue", "Y")));
            doReturn(mockResult).when(feignClient).getDictByType("YOrN", "all");

            AuthCommonProvider provider = new FeignAuthCommonProvider(feignClient);
            CommonResult<?> result = provider.getDictByType("YOrN", "all");

            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            verify(feignClient).getDictByType("YOrN", "all");
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("listAllOrg: 应委托 FeignClient 调用")
        void shouldDelegateListAllOrg() {
            AuthFeignClient feignClient = mock(AuthFeignClient.class);
            CommonResult<?> mockResult = CommonResult.success(Collections.singletonList(mapOf("orgId", "1001", "orgName", "测试机构")));
            doReturn(mockResult).when(feignClient).listAllOrg();

            AuthCommonProvider provider = new FeignAuthCommonProvider(feignClient);
            CommonResult<?> result = provider.listAllOrg();

            assertEquals("0000", result.getCode());
            assertNotNull(result.getData());
            verify(feignClient).listAllOrg();
        }
    }
}