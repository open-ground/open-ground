package io.github.openground.land.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;

@Data
@TableName("TASK_DISPATCH_CONFIG")
public class TaskDispatchConfigDomain extends BasePo {

    @TableId
    private String taskId;

    /**
     * 法人
     */
    private String company;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务执行地址
     */
    private String taskAddress;

    /**
     * 执行方法名
     */
    private String taskMethod;

    /**
     * 执行周期值
     */
    private String exePeriodValue;

    /**
     * 执行周期类型
     */
    private String exePeriodType;

    /**
     * 执行周期类型名称
     */
    private String exePeriodTypeName;

    /**
     * 第一次执行时间
     */
    private String firstExeTime;

    /**
     * 任务是否有效
     */
    private String status;

    /**
     * 是否追加历史未执行
     */
    private String additionFlag;

    /**
     * 执行任务主机IP
     */
    private String exeHostIp;

    /**
     * 下次执行时间
     */
    private String nextExeTime;

    /**
     * 任务是否正在执行
     */
    private String taskIsRunning;

    /**
     * 任务明细
     */
    private String taskDetail;

    /**
     * 创建时间
     */
    private String mtTime;

    /**
     * 创建人
     */
    private String mtUser;

    /**
     * 已重跑次数
     */
    private Integer reExeTimes;

    /**
     * 最大重跑次数
     */
    private Integer maxReExeTimes;

    /**
     * 传入参数
     */
    private String params;

    /**
     * 条件参数
     */
    private String conditionParam;

    /**
     * 开始执行时间
     */
    private String startTime;

    /**
     * 结束执行时间
     */
    private String endTime;

    /**
     * 是否有前置任务
     */
    private String isBefore;

    /**
     * 前置任务
     */
    private String beforeTask;

    /**
     * 后置任务
     */
    private String afterTask;

    /**
     * 批次号
     */
    private Integer batchNo;

    /**
     * 更新时间
     */
    private String updateTime;

    /**
     * 文件信息
     */
    private String fileInfo;

    /**
     * 执行计划ID
     */
    private String id;

    /**
     * 是否有后置任务
     */
    private String isAfter;

    /**
     * 是否满足执行条件
     */
    private String isExePeriod;

    /**
     * 调度组
     */
    private String cpsGroup;

    /** 作业ID */
    private String jobId;

    /** 作业ID */
    private String jobFlow;

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

    /** 查询排序 */
    private String queryOrder;

}
