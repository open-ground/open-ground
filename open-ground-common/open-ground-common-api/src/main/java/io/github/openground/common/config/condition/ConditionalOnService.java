package io.github.openground.common.config.condition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * Service 模式条件（分离部署）
 * <p>当 {@code ground.mode=service} 时生效。</p>
 * <p>Service 模式下，组件使用远程调用方式工作（Feign 等），依赖 Auth 服务。</p>
 *
 * @author open-ground
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "ground", name = "mode", havingValue = "service")
public @interface ConditionalOnService {
}
