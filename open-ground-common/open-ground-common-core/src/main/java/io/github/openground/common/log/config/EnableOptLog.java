package io.github.openground.common.log.config;

import io.github.openground.common.log.aspect.OptLogAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用操作日志组件
 * <p>在业务模块的启动类或配置类上添加此注解：</p>
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableOptLog
 * public class FlowApplication { ... }
 * </pre>
 * <p>启用后自动装配 {@link OptLogAspect}（切面）和 {@code OptLogEventListener}（异步监听器）。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(OptLogAutoConfiguration.class)
public @interface EnableOptLog {
}
