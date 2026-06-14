package io.github.openground.common.security.spi;

import lombok.Builder;
import lombok.Data;

import java.util.Collection;
import java.util.Date;

/**
 * UserDetails 默认实现
 * <p>提供基本的用户信息存储，业务模块可继承或直接使用。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
@Builder
public class DefaultUserDetails implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String mobile;
    private String email;
    private String avatar;
    private Collection<Long> roleIds;
    private Collection<String> roleNames;
    private Collection<String> authorities;
    private String orgId;
    private String orgName;
    private Long corpId;
    private String corpName;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private boolean enabled;
    private Date createTime;
    private Date lastLoginTime;
    private String extData;
    private String clientIp;

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}