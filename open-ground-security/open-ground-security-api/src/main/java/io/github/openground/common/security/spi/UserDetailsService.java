package io.github.openground.common.security.spi;

/**
 * 用户详情服务接口
 * <p>对标 Spring Security 的 UserDetailsService</p>
 * <p>业务模块需提供此接口的实现，用于根据用户名、手机号、用户ID等查询用户详情。</p>
 *
 * @author open-ground
 * @version 1.0
 * @see UserDetails
 */
public interface UserDetailsService {

    /**
     * 根据用户名加载用户详情
     * <p>核心方法，用于登录认证、Token 生成等场景</p>
     *
     * @param username 用户名
     * @return 用户详情
     * @throws UserNotFoundException 用户不存在时抛出
     */
    UserDetails loadUserByUsername(String username);

    /**
     * 根据手机号加载用户详情（可选实现）
     * <p>用于短信验证码登录场景</p>
     *
     * @param mobile 手机号
     * @return 用户详情
     * @throws UserNotFoundException 用户不存在时抛出
     */
    default UserDetails loadUserByMobile(String mobile) {
        throw new UnsupportedOperationException("loadUserByMobile not implemented");
    }

    /**
     * 根据用户ID加载用户详情（可选实现）
     *
     * @param userId 用户ID
     * @return 用户详情
     * @throws UserNotFoundException 用户不存在时抛出
     */
    default UserDetails loadUserById(Long userId) {
        throw new UnsupportedOperationException("loadUserById not implemented");
    }
}