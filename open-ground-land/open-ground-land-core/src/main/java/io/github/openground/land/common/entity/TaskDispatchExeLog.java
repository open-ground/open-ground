package io.github.openground.land.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.openground.land.common.dao.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author jack.zhang
 * @Description 任务执行计划表
 * @Date 2022-04-20 16:11:35
 * @Version 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("TASK_DISPATCH_EXE_LOG")
public class TaskDispatchExeLog extends BasePo {

    @TableId
    private String id;

    private String company;

    private String jobId;

    private String eodDate;

    private String taskId;

    private String planStartTime;

    private String exeStartTime;

    private String exeEndTime;

    private String exeStatus;

    private String errInfo;

    private String exeCurHostIp;

    private String mtTime;

    private String batchNo;

    private String fileName;

    private String extend1;

    private String extend2;

    private String extend3;

    private String extend4;

    private String extend5;

}
