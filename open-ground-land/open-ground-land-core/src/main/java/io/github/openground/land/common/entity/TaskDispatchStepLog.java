package io.github.openground.land.common.entity;

import lombok.Data;

/**
 * 任务分段执行记录表
 * <p>表名：TASK_DISPATCH_STEP_LOG</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Data
public class TaskDispatchStepLog {

    /** 执行计划id */
    private String taskPlanId;

    /** 分段id */
    private String stepId;

    /** 任务id */
    private String taskId;

    /** 执行器地址 */
    private String exeUrl;

    /** 分段 */
    private String segment;

    /** 线程名称 */
    private String threadName;

    /** 执行信息 */
    private String infoMessage;

    /** 异常信息 */
    private String exceptionMessage;

    /** 状态 SUCCESS-执行成功，RUNING-执行中 */
    private String stepStatus;

    /** 操作时间 */
    private String mtTime;

    /** 法人 */
    private String company;

    /** 扩展字段 */
    private String extend1;

    /** 扩展字段 */
    private String extend2;

    /** 扩展字段 */
    private String extend3;

    /** 扩展字段 */
    private String extend4;

    /** 扩展字段 */
    private String extend5;
}
