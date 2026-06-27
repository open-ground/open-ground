package io.github.openground.common.config.condition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * POC 功能标记注解
 *
 * <p>标记在 Controller、Service、Configuration 等类或方法上，
 * 只有 {@code ground.poc.enabled=true} 时才会加载该 Bean。</p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>未经过测试上线的实验性功能</li>
 *   <li>临时 POC 验证功能</li>
 *   <li>需要按环境开关的功能</li>
 * </ul>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * # 生产环境（不加载 POC 代码）
 * ground.poc.enabled: false
 *
 * # POC 环境（加载 POC 代码）
 * ground.poc.enabled: true
 * }</pre>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/poc/ai")
 * @PocFeature("AI 智能分析")
 * public class AiPocController { ... }
 * }</pre>
 *
 * @author open-ground
 * @version 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "ground.poc", name = "enabled", havingValue = "true")
public @interface PocFeature {

    /**
     * 功能描述
     *
     * @return POC 功能说明
     */
    String value() default "";
}
