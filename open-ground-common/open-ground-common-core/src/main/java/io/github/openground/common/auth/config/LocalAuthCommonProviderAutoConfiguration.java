package io.github.openground.common.auth.config;

import io.github.openground.common.auth.AuthCommonProvider;
import io.github.openground.common.auth.LocalAuthCommonProvider;
import io.github.openground.common.config.condition.ConditionalOnAuth;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LocalAuthCommonProvider 自动配置（Auth 集成部署模式）
 *
 * <p>当 {@code ground.mode=auth}（默认值）时生效，通过 {@link DynamicJdbcTemplate}
 * 直连数据库实现 {@link AuthCommonProvider} 接口。</p>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
@AutoConfiguration
@ConditionalOnAuth
public class LocalAuthCommonProviderAutoConfiguration {

    /**
     * 直连数据库实现的 AuthCommonProvider
     * <p>当容器中未注入其他 {@link AuthCommonProvider} Bean 时自动装配。</p>
     */
    @Bean
    @ConditionalOnMissingBean(AuthCommonProvider.class)
    public AuthCommonProvider localAuthCommonProvider(DynamicJdbcTemplate dynamicJdbcTemplate,
                                                       NamedParameterJdbcTemplate primaryJdbcTemplate) {
        log.info("初始化 LocalAuthCommonProvider（Auth 模式，直连数据库）");
        return new LocalAuthCommonProvider(dynamicJdbcTemplate, primaryJdbcTemplate);
    }
}