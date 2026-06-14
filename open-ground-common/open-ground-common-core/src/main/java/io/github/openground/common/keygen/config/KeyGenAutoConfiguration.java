package io.github.openground.common.keygen.config;

import io.github.openground.common.config.condition.ConditionalOnAuth;
import io.github.openground.common.keygen.JdbcSequenceProvider;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.keygen.SequenceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 主键生成器自动配置（集成部署模式）
 * <p>
 * <b>设计原则：</b>通过 SPI 接口 {@link SequenceProvider} 解耦。
 * </p>
 * <ul>
 *   <li>集成部署 → 自动装配 {@link JdbcSequenceProvider}（直连数据库）</li>
 *   <li>分离部署 → 由 {@code open-ground-common-springcloud} 模块提供 {@link SequenceProvider} 实现</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
@AutoConfiguration
@ConditionalOnAuth
public class KeyGenAutoConfiguration {

    /**
     * 默认 JdbcSequenceProvider
     * <p>当容器中没有其他 {@link SequenceProvider} Bean 时自动装配。</p>
     */
    @Bean
    @ConditionalOnMissingBean(SequenceProvider.class)
    public SequenceProvider jdbcSequenceProvider(DataSourceTransactionManager txManager) {
        log.info("初始化 JdbcSequenceProvider（集成部署模式，直连数据库）");
        return new JdbcSequenceProvider(txManager);
    }

    /**
     * 主键生成器
     * <p>自动注入已存在的 {@link SequenceProvider}，并注册为静态默认实例。</p>
     */
    @Bean
    public KeyGenerator keyGenerator(SequenceProvider sequenceProvider) {
        KeyGenerator generator = new KeyGenerator(sequenceProvider);
        KeyGenerator.init(generator);
        log.info("初始化 KeyGenerator 完成");
        return generator;
    }
}
