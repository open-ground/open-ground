package io.github.openground.common.log.annotation;

import io.github.openground.common.log.enums.OptType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * <p>标注在 Controller 方法上，由 {@link io.github.openground.common.log.aspect.OptLogAspect} 切面拦截处理。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptLog {

    /**
     * 操作内容描述
     */
    String optRemark() default "";

    /**
     * 操作类型
     */
    OptType optType() default OptType.OTHER;

    /**
     * 是否保存请求的参数
     */
    boolean isSaveRequestData() default true;
}
