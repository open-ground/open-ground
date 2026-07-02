package io.github.openground.common.excel.annotation;

import io.github.openground.common.excel.enums.QueryType;
import io.github.openground.common.excel.spi.ExcelQueryProvider;

import java.lang.annotation.*;

/**
 * Excel 导入导出模板注解
 * <p>标注在 VO 类上，声明该类的 Excel 导入导出配置。</p>
 *
 * <h3>零代码使用（TABLE 模式）</h3>
 * <pre>{@code
 * @ExcelTemplate(tableName = "sys_user")
 * public class UserExcelVO {
 *     @ExcelField(headerName = "用户名")
 *     private String username;
 * }
 * }</pre>
 * 启动后自动注册端点：<pre>POST /ground/excel/user-excel-vo/export</pre>
 *
 * @author open-ground
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelTemplate {

    /**
     * Excel Sheet 名称
     */
    String sheetName() default "Sheet1";

    /**
     * 数据来源类型：TABLE / SQL / CUSTOM
     */
    QueryType queryType() default QueryType.TABLE;

    /**
     * 单表模式下的表名（queryType = TABLE 时必填）
     */
    String tableName() default "";

    /**
     * 自定义查询/落库提供者（queryType = CUSTOM 时使用）
     */
    Class<? extends ExcelQueryProvider> queryProvider() default VoidExcelQueryProvider.class;

    /**
     * 自定义 URL 路径段（不填则自动取类名转 kebab-case）
     */
    String name() default "";

    /**
     * 是否在 Excel 中导出表头
     */
    boolean needHead() default true;

    /**
     * 内部标记类，表示未设置 queryProvider
     */
    abstract class VoidExcelQueryProvider implements ExcelQueryProvider {}
}
