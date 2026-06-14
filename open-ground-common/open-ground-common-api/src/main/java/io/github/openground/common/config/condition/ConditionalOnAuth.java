package io.github.openground.common.config.condition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * Auth 模式条件（集成部署）
 * <p>当 {@code ground.mode=auth}（默认值）时生效。</p>
 * <p>Auth 模式下，组件使用直连数据库方式工作（JdbcTemplate 等），不依赖远程服务。</p>
 *
 * @author open-ground
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "ground", name = "mode", havingValue = "auth", matchIfMissing = true)
public @interface ConditionalOnAuth {
}
