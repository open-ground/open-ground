package io.github.openground.land.config;

import lombok.Data;

import java.util.List;

/**
 * <p>Title: FileConfig</p>
 * <p>Description: 文件配置</P>
 *
 * @Author:jack.zhang
 * @Date 2021/7/28 21:15
 * @Version 3.0.0
 */
@Data
public class FileConfig {

    /**
     * 描述
     */
    private String description;

    /**
     * 作者
     */
    private String author;

    /**
     * 数据库表名称
     */
    private String tableName;

    /**
     * 数据文件名称
     */
    private String fileName;

    /**
     * 删除条件字段
     */
    private String deleteCol;

    /**
     * 删除条件字段
     */
    private String updateCol;

    /**
     * 文件类型：增量 or 全量
     */
    private String fileType;

    /**
     * 文件分隔符
     */
    private String fileSplit;

    /**
     * 文件每行有多少列
     */
    private int fileColNum;

    /**
     * 文件每行有多少列
     */
    private String isCreateOkFile;

    /**
     * 文件每行有多少列
     */
    private int jobStep;

    /**
     * 文件行过滤器
     */
    private String jobFilter;

    /**
     * 数据配置
     */
    private List<DataConfig> dataConfig;
}
