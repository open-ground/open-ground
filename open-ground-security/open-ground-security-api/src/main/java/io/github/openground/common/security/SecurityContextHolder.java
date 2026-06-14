package io.github.openground.common.security;

import io.github.openground.common.security.spi.UserDetails;

/**
 * 安全上下文持有者
 * <p>对标 Spring Security 的 SecurityContextHolder，管理当前用户的会话上下文。</p>
 * <p>使用 ThreadLocal 实现线程隔离，请求结束后必须调用 {@link #clear()} 清除上下文。</p>
 *
 * @author open-ground
 * @version 1.0
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<UserDetails> CURRENT_USER = new ThreadLocal<>();

    private SecurityContextHolder() {
    }

    /**
     * 设置当前用户
     * @param userDetails 用户详情
     */
    public static void setCurrentUser(UserDetails userDetails) {
        CURRENT_USER.set(userDetails);
    }

    /**
     * 获取当前用户
     * @return 用户详情，未登录返回 null
     */
    public static UserDetails getCurrentUser() {
        return CURRENT_USER.get();
    }

    /**
     * 获取当前用户名
     * @return 用户名，未登录返回 null
     */
    public static String getCurrentUsername() {
        UserDetails user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 获取当前用户ID
     * @return 用户ID，未登录返回 null
     */
    public static Long getCurrentUserId() {
        UserDetails user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 清除当前用户上下文
     * <p>请求结束后必须调用，防止内存泄漏</p>
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}