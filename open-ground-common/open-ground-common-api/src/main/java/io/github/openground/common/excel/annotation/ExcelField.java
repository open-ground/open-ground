package io.github.openground.common.excel.annotation;

import io.github.openground.common.excel.spi.ExcelDataValidator;

import java.lang.annotation.*;

/**
 * Excel 字段注解
 * <p>标注在 VO 类的字段上，配置 Excel 列头、顺序、字典翻译、校验等。</p>
 *
 * @author open-ground
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelField {

    /**
     * Excel 列头名称
     */
    String headerName();

    /**
     * 列顺序（数值越小越靠左）
     */
    int order() default 0;

    /**
     * 字典类型
     * <p>导出时：将 code 翻译为 label；导入时：将 label 翻译为 code。</p>
     * <p>为空表示不翻译。</p>
     */
    String dictType() default "";

    /**
     * 日期格式（如 yyyy-MM-dd HH:mm:ss）
     */
    String dateFormat() default "";

    /**
     * 导入时是否必填
     */
    boolean required() default false;

    /**
     * 导入自定义校验器
     */
    Class<? extends ExcelDataValidator> validator() default VoidValidator.class;

    /**
     * 导出时是否忽略此字段
     */
    boolean exportIgnore() default false;

    /**
     * 导入时是否忽略此字段
     */
    boolean importIgnore() default false;

    /**
     * 内部标记类，表示未设置校验器
     */
    abstract class VoidValidator implements ExcelDataValidator {}
}
