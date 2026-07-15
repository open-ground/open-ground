package io.github.openground.test.config;

import io.github.openground.common.datasource.dto.RoleInfo;
import io.github.openground.common.datasource.spi.RoleProvider;
import io.github.openground.common.security.spi.DefaultUserDetails;
import io.github.openground.common.security.spi.UserDetails;
import io.github.openground.common.security.spi.UserDetailsService;
import io.github.openground.common.security.spi.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/**
 * 测试配置类
 * <p>提供模拟的 UserDetailsService 和 TestLogSender，供 TokenManager / OptLog 等组件测试使用</p>
 *
 * @author open-ground
 */
@Slf4j
@Configuration
public class TestConfig {

    /**
     * 模拟的 UserDetailsService 实现
     * <p>提供固定的测试用户数据，不依赖真实数据库</p>
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService userDetailsService() {
        log.info("初始化测试用 UserDetailsService");
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) {
                if (username == null || username.isEmpty()) {
                    throw new UserNotFoundException(username);
                }
                return DefaultUserDetails.builder()
                        .id(1L)
                        .username(username)
                        .password("{noop}test123")
                        .realName("测试用户")
                        .mobile("13800138000")
                        .email(username + "@test.com")
                        .roleIds(Arrays.asList(1L, 2L))
                        .roleNames(Arrays.asList("ROLE_ADMIN", "ROLE_USER"))
                        .authorities(Arrays.asList("user:read", "user:write"))
                        .orgId("1001")
                        .orgName("测试机构")
                        .corpId(1L)
                        .corpName("测试法人")
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .enabled(true)
                        .createTime(new Date())
                        .lastLoginTime(System.currentTimeMillis())
                        .clientIp("127.0.0.1")
                        .build();
            }
        };
    }

    /**
     * 模拟的 RoleProvider 实现
     * <p>提供固定的测试角色数据，供数据源表权限测试使用</p>
     */
    @Bean
    @ConditionalOnMissingBean(RoleProvider.class)
    public RoleProvider roleProvider() {
        log.info("初始化测试用 RoleProvider");
        return () -> Arrays.asList(
                new RoleInfo("1", "ROLE_ADMIN"),
                new RoleInfo("2", "ROLE_USER")
        );
    }

}
