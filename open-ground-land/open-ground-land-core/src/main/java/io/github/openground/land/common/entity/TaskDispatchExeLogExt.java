package io.github.openground.land.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author jack.zhang
 * @Description 任务执行计划表
 * @Date 2020-07-10 00:06:25
 * @Version 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TaskDispatchExeLogExt extends TaskDispatchExeLog {

    private String beforeTask;

    private String afterTask;

    private String taskName;

    /** 作业ID */
    private String jobId;

    /** 作业ID */
    private String jobFlow;

    /**
     * 执行service
     */
    private String taskMethod;

}
