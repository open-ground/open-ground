package io.github.openground.common.excel.resolver;

import lombok.Builder;
import lombok.Getter;

/**
 * Excel 字段元数据
 *
 * @author open-ground
 */
@Getter
@Builder
public class FieldMeta {

    /** 字段名 */
    private String fieldName;

    /** Excel 列头名称 */
    private String headerName;

    /** 列顺序 */
    private int order;

    /** 字典类型 */
    private String dictType;

    /** 日期格式 */
    private String dateFormat;

    /** 是否必填 */
    private boolean required;

    /** 自定义校验器类名 */
    private String validatorClass;

    /** 是否导出忽略 */
    private boolean exportIgnore;

    /** 是否导入忽略 */
    private boolean importIgnore;

    /** 字段类型 */
    private Class<?> fieldType;

    public boolean hasDictType() {
        return dictType != null && !dictType.isEmpty();
    }

    public boolean hasDateFormat() {
        return dateFormat != null && !dateFormat.isEmpty();
    }
}
