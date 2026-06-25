package io.github.openground.land.config;

import lombok.Data;

/**
 * <p>Title: DataConfig</p>
 * <p>Description: 数据映射配置</P>
 *
 * @Author:jack.zhang
 * @Date 2021/7/28 21:59
 * @Version 3.0.0
 */
@Data
public class DataConfig {

    /**
     * 数据库列名
     */
    private String dbColumn;

    /**
     * 字段在文件中对应的列下标，从0开始数
     */
    private int index;

    /**
     * 数据字段类型，可选择 String、Number、
     */
    private String dataType;

    /**
     * 默认值，当没有指定列下标时取该值入库
     */
    private String defaultValue;
}
