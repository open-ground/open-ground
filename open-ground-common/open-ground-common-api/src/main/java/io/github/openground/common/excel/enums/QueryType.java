package io.github.openground.common.excel.enums;

/**
 * Excel 数据查询类型
 *
 * @author open-ground
 */
public enum QueryType {

    /**
     * 单表查询：根据 tableName 自动生成 SELECT * FROM tableName
     */
    TABLE,

    /**
     * 自定义 SQL：执行配置的 SQL 语句
     */
    SQL,

    /**
     * 自定义 Service：调用 ExcelQueryProvider 的实现
     */
    CUSTOM
}
