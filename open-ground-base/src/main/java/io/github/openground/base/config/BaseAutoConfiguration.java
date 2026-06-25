package io.github.openground.base.config;

import io.github.openground.base.constant.ErrorCodeMapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Base 模块自动配置
 *
 * <p>通过 {@code @ComponentScan} 扫描 base 模块下的组件（如 {@link ErrorCodeMapper}），
 * 使业务项目无需额外配置即可使用基础能力。</p>
 *
 * @author open-ground
 */
@Configuration
@ComponentScan(basePackages = "io.github.openground.base")
public class BaseAutoConfiguration {
}
