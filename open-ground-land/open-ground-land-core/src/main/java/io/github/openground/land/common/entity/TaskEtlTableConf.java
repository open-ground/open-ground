package io.github.openground.land.common.entity;

import lombok.Data;

/**
 * 入库任务配置表
 * <p>表名：TASK_ETL_TABLE_CONF</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Data
public class TaskEtlTableConf {

    /** ID */
    private String id;

    /** 表名 */
    private String tableName;

    /** 文件名称 */
    private String fileName;

    /** 文件分段数 */
    private Integer fileStep;

    /** 文件分隔符 */
    private String fileSplit;

    /** 文件列数 */
    private Integer fileColNum;

    /** 执行状态 S-成功 F-失败 R-执行中 P-待执行 */
    private String exeStatus;

    /** 执行信息 */
    private String exeMessage;

    /** 耗时:秒 */
    private String exeTime;

    /** 执行ip */
    private String exeIp;

    /** 全量标志Y-全量 N-增量 */
    private String isFull;

    /** 删除数据语句 */
    private String deleteSql;

    /** 是否生成OK文件 Y-是 N-否 */
    private String okFlag;

    /** 异常是否停止 */
    private String isErrStop;

    /** 是否有效 Y-有效 */
    private String status;

    /** 数据配置 */
    private String dataConfig;

    /** 扩展字段 */
    private String extend1;

    /** 扩展字段 */
    private String extend2;

    /** 扩展字段 */
    private String extend3;
}
