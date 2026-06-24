package io.github.openground.common.security;

import com.alibaba.fastjson.JSON;
import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.exception.CommonException;
import io.github.openground.common.security.spi.DefaultUserDetails;
import io.github.openground.common.security.spi.UserDetails;
import io.github.openground.common.security.spi.UserDetailsService;
import io.github.openground.common.security.spi.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Token 管理器
 * <p>负责 Token 的生成、验证、刷新、销毁，以及会话上下文管理</p>
 * <p>功能对标 ground-auth-core 的 AuthUtil，保持业务逻辑一致</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Component
public class TokenManager {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired(required = false)
    private ApiKeyService apiKeyService;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private AuthProperties authProperties;

    /**
     * Token 刷新黑名单路径
     * 这些请求不会触发 Token 的有效期刷新，避免频繁刷新影响性能
     */
    private static final Set<String> REFRESH_BLACKLIST = Collections.singleton("/auth/uaa/info/list");

    /**
     * 当前请求的会话上下文
     * 使用 ThreadLocal 存储当前线程的会话信息，实现线程隔离
     */
    private static final ThreadLocal<SessionEntity> CURRENT_SESSION = new ThreadLocal<>();

    /**
     * 登录成功后生成 Token 并保存会话数据
     * <p>支持多设备登录控制、IP 地址校验、强制登录等安全特性</p>
     *
     * @param userDetails 用户信息
     * @param grantType   授权类型（如 password、sso、api_key 等）
     * @param parameters  登录参数，可能包含强制登录标识等
     * @return 生成的 Token 字符串
     */
    public String generateToken(UserDetails userDetails, String grantType, Map<String, String> parameters) {
        try {
            String userInfoStr = JSON.toJSONString(userDetails);
            SessionEntity session = new SessionEntity();
            long expireTime = System.currentTimeMillis() + authProperties.getTokenTimeout() * 60 * 1000L;
            session.setLastAccessTime(new Date());
            session.setSessionData(userInfoStr);

            // 查询用户是否已有会话
            List<SessionEntity> list = tokenStore.findByUsername(userDetails.getUsername());
            if (list != null && !list.isEmpty()) {
                session = list.get(0);
                // 多设备登录控制
                if (!authProperties.getMultiLogin() && parameters != null && !parameters.containsKey("kick")) {
                    UserDetails loginUser = null;
                    try {
                        loginUser = JSON.parseObject(session.getSessionData(), DefaultUserDetails.class);
                        // IP 地址校验：使用 UserDetails.getClientIp() 直接字段
                        if (userDetails.getClientIp() != null
                                && loginUser.getClientIp() != null
                                && !userDetails.getClientIp().equals(loginUser.getClientIp())) {
                            log.error("用户已在其他地方登录，IP：{}", loginUser.getClientIp());
                            throw new CommonException(ErrorCode.NO_LOGIN, "用户已在其他地方登录");
                        }
                    } catch (CommonException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("read session data error：", e);
                    }
                }
                session.setExpireTime(paseTime(expireTime));
                session.setHost(userDetails.getClientIp());
                log.info("user:{} is login, update session:{}", userDetails.getUsername(), session.getId());
                tokenStore.update(session);
            } else {
                String token = UUID.randomUUID().toString();
                session.setId(token);
                session.setToken(token);
                session.setCreateTime(new Date());
                session.setUsername(userDetails.getUsername());
                session.setGrantType(grantType);
                session.setExpireTime(paseTime(expireTime));
                session.setHost(userDetails.getClientIp());
                tokenStore.save(session);
            }
            return session.getToken();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录异常", e);
            throw new RuntimeException("Failed to generate token", e);
        }
    }

    /**
     * SSO 单点登录生成 Token
     * <p>用于 SSO 单点登录场景，根据用户名生成访问 Token</p>
     *
     * @param username 用户名
     * @return Token 字符串
     */
    public String generateTokenSso(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails == null) {
            throw new UserNotFoundException(username);
        }
        return generateToken(userDetails, "sso", null);
    }

    /**
     * 校验 Token 是否有效，并可选刷新有效期
     *
     * @param token       要校验的 Token 字符串
     * @param requestPath 当前请求路径，用于判断是否在刷新黑名单中
     * @return 会话信息，如果 Token 无效则返回 null
     */
    public SessionEntity validateAndRefreshToken(String token, String requestPath) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        SessionEntity session = tokenStore.findById(token);
        if (session == null) {
            return null;
        }

        if (paseTime(System.currentTimeMillis()) > session.getExpireTime()) {
            log.info("user: {}, token expired, clean token: {}", session.getUsername(), token);
            tokenStore.deleteById(token);
            return null;
        }

        if (!REFRESH_BLACKLIST.contains(requestPath)) {
            long newExpireTime = System.currentTimeMillis() + authProperties.getTokenTimeout() * 60 * 1000L;
            session.setExpireTime(paseTime(newExpireTime));
            session.setLastAccessTime(new Date());
            log.debug("user: {}, token refresh: {}", session.getUsername(), newExpireTime);
            tokenStore.update(session);
        }

        return session;
    }

    /**
     * 校验 Token 是否有效（不刷新）
     *
     * @param token Token 字符串
     * @return 用户详情，验证失败返回 null
     */
    public UserDetails validateToken(String token) {
        SessionEntity session = tokenStore.findById(token);
        if (session == null || isExpired(session)) {
            return null;
        }
        return deserializeUserDetails(session.getSessionData());
    }

    /**
     * 清理过期的会话
     */
    public void cleanupExpiredSessions() {
        tokenStore.cleanExpired(paseTime(System.currentTimeMillis()));
    }

    /**
     * 注销 Token
     *
     * @param token 要注销的 Token 字符串
     */
    public void invalidateToken(String token) {
        log.info("注销登录，清理 session：{}", token);
        tokenStore.deleteById(token);
    }

    /**
     * 销毁用户的所有 Token
     *
     * @param username 用户名
     */
    public void invalidateUserTokens(String username) {
        tokenStore.deleteByUsername(username);
    }

    /**
     * 校验 API Key 并设置当前会话
     * <p>使用 ApiKeyService 校验 API Key 的有效性，校验通过后构建虚拟会话并设置到 ThreadLocal</p>
     *
     * @param apiKey API Key 值
     * @return 校验通过后的会话信息
     */
    public SessionEntity validateApiKey(String apiKey) {
        if (apiKeyService == null) {
            throw new RuntimeException("ApiKeyService 未配置");
        }

        ApiKeyDO keyDO = apiKeyService.validateKey(apiKey);

        // 查询用户信息
        UserDetails userDetails = userDetailsService.loadUserByUsername(keyDO.getUsername());
        if (userDetails == null) {
            throw new UserNotFoundException(keyDO.getUsername());
        }

        // 构建虚拟会话
        SessionEntity session = new SessionEntity();
        session.setId(apiKey);
        session.setToken(apiKey);
        session.setUsername(keyDO.getUsername());
        session.setGrantType("api_key");
        session.setCreateTime(new Date());
        session.setLastAccessTime(new Date());

        // 设置 sessionData 供 getCurrentUser() 反序列化
        try {
            session.setSessionData(JSON.toJSONString(userDetails));
        } catch (Exception e) {
            log.error("序列化用户信息失败", e);
        }

        // 设置当前会话到 ThreadLocal
        setCurrentSession(session);

        // 同步设置 SecurityContextHolder
        SecurityContextHolder.setCurrentUser(userDetails);

        log.debug("API Key 认证通过: user={}", keyDO.getUsername());
        return session;
    }

    // ==================== 会话上下文管理 ====================

    /**
     * 将当前会话放入 ThreadLocal
     *
     * @param entity 会话实体对象
     */
    public void setCurrentSession(SessionEntity entity) {
        CURRENT_SESSION.set(entity);
    }

    /**
     * 获取当前线程中的会话实体
     *
     * @return 当前线程的会话实体，如果未设置则返回 null
     */
    public SessionEntity getCurrentSession() {
        return CURRENT_SESSION.get();
    }

    /**
     * 获取当前线程中的用户信息
     *
     * @return 当前用户信息对象，如果获取失败则返回 null
     */
    public UserDetails getCurrentUser() {
        UserDetails user = null;
        try {
            if (CURRENT_SESSION.get() != null) {
                user = JSON.parseObject(CURRENT_SESSION.get().getSessionData(), DefaultUserDetails.class);
            }
        } catch (Exception e) {
            log.warn("Get current user is null");
        }
        return user;
    }

    /**
     * 清除 ThreadLocal 中的会话信息
     * <p>必须在请求处理完成后调用此方法，否则可能导致内存泄漏</p>
     */
    public void clearCurrentSession() {
        CURRENT_SESSION.remove();
    }

    // ==================== 私有方法 ====================

    /**
     * 转换格式为时间戳，方便查看
     *
     * @param expireTime 时间戳（毫秒）
     * @return yyyyMMddHHmmss 格式的时间戳
     */
    public static long paseTime(long expireTime) {
        Date expDate = new Date(expireTime);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String dateStr = sdf.format(expDate);
        return Long.parseLong(dateStr);
    }

    private boolean isExpired(SessionEntity session) {
        return paseTime(System.currentTimeMillis()) > session.getExpireTime();
    }

    private UserDetails deserializeUserDetails(String data) {
        return JSON.parseObject(data, DefaultUserDetails.class);
    }
}