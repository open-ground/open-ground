package io.github.openground.common.security.spi;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;

/**
 * 用户详情接口
 * <p>对标 Spring Security 的 UserDetails，包含用户核心属性。</p>
 * <p>业务模块通过实现此接口来提供用户信息。</p>
 *
 * @author open-ground
 * @version 1.0
 */
public interface UserDetails extends Serializable {

    /**
     * 获取用户ID
     * @return 用户ID
     */
    Long getId();

    /**
     * 获取用户名
     * @return 用户名
     */
    String getUsername();

    /**
     * 获取密码（加密后）
     * @return 密码
     */
    String getPassword();

    /**
     * 获取真实姓名
     * @return 真实姓名
     */
    String getRealName();

    /**
     * 获取手机号
     * @return 手机号
     */
    String getMobile();

    /**
     * 获取邮箱
     * @return 邮箱
     */
    String getEmail();

    /**
     * 获取头像URL
     * @return 头像URL
     */
    String getAvatar();

    /**
     * 获取角色ID集合
     * @return 角色ID集合
     */
    Collection<Long> getRoleIds();

    /**
     * 获取角色名称集合
     * @return 角色名称集合
     */
    Collection<String> getRoleNames();

    /**
     * 获取权限集合（如 "user:read", "user:write"）
     * @return 权限集合
     */
    Collection<String> getAuthorities();

    /**
     * 获取机构ID
     * @return 机构ID
     */
    String getOrgId();

    /**
     * 获取机构名称
     * @return 机构名称
     */
    String getOrgName();

    /**
     * 获取法人ID
     * @return 法人ID
     */
    Long getCorpId();

    /**
     * 获取法人名称
     * @return 法人名称
     */
    String getCorpName();

    /**
     * 账户是否未过期
     * @return true 未过期
     */
    default boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定
     * @return true 未锁定
     */
    default boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期
     * @return true 未过期
     */
    default boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否启用
     * @return true 启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取创建时间
     * @return 创建时间
     */
    Date getCreateTime();

    /**
     * 获取最后登录时间
     * @return 最后登录时间
     */
    Date getLastLoginTime();

    /**
     * 获取扩展数据（JSON格式）
     * @return 扩展数据
     */
    String getExtData();

    /**
     * 获取客户端IP
     * @return 客户端IP
     */
    String getClientIp();
}