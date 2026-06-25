package io.github.openground.land.api.dto;

import io.github.openground.base.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 任务中心请求参数
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务中心请求参数")
public class TaskCenterRequest extends BaseRequest {

    // ==================== 任务配置字段 ====================

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务执行地址")
    private String taskAddress;

    @Schema(description = "执行方法名")
    private String taskMethod;

    @Schema(description = "执行周期值")
    private String exePeriodValue;

    @Schema(description = "执行周期类型")
    private String exePeriodType;

    @Schema(description = "第一次执行时间")
    private String firstExeTime;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "是否追加历史未执行")
    private String additionFlag;

    @Schema(description = "执行任务主机IP")
    private String exeHostIp;

    @Schema(description = "下次执行时间")
    private String nextExeTime;

    @Schema(description = "任务是否正在执行")
    private String taskIsRunning;

    @Schema(description = "任务明细")
    private String taskDetail;

    @Schema(description = "创建人")
    private String mtUser;

    @Schema(description = "已重跑次数")
    private Long reExeTimes;

    @Schema(description = "最大重跑次数")
    private Long maxReExeTimes;

    @Schema(description = "传入参数")
    private String params;

    @Schema(description = "条件参数")
    private String conditionParam;

    @Schema(description = "开始执行时间")
    private String startTime;

    @Schema(description = "结束执行时间")
    private String endTime;

    @Schema(description = "是否有前置任务")
    private String isBefore;

    @Schema(description = "前置任务")
    private String beforeTask;

    @Schema(description = "后置任务")
    private String afterTask;

    @Schema(description = "批次号")
    private Integer batchNo;

    @Schema(description = "更新时间")
    private String updateTime;

    @Schema(description = "文件信息")
    private String fileInfo;

    @Schema(description = "调度组")
    private String cpsGroup;

    // ==================== 执行计划字段 ====================

    @Schema(description = "执行计划ID")
    private String id;

    @Schema(description = "修改前的前置任务")
    private String oldBeforeTask;

    @Schema(description = "是否任务启停")
    private String isStartOrStop;

    @Schema(description = "主机IP")
    private String hostIp;

    @Schema(description = "引擎状态")
    private String activeStatus;

    @Schema(description = "执行状态")
    private String exeStatus;

    @Schema(description = "执行信息")
    private String errInfo;

    @Schema(description = "执行日期")
    private String exeDate;

    @Schema(description = "作业ID")
    private String jobId;

    @Schema(description = "跑批日期")
    private String eodDate;

    @Schema(description = "作业批次号")
    private String jobBatchNo;

    @Schema(description = "执行类型")
    private String exeType;

    @Schema(description = "作业流程图配置")
    private String jobFlow;

    // ==================== 扩展字段 ====================

    @Schema(description = "扩展字段1")
    private String extend1;

    @Schema(description = "扩展字段2")
    private String extend2;

    @Schema(description = "扩展字段3")
    private String extend3;

    @Schema(description = "扩展字段4")
    private String extend4;

    @Schema(description = "扩展字段5")
    private String extend5;

    @Schema(description = "子任务ID")
    private String subTaskId;

    // ==================== 批量操作字段 ====================

    @Schema(description = "批量数据")
    private List<Map<String, Object>> batchList;

    // ==================== 当前页/每页条数（快捷字段，也可通过 appHead 传递） ====================

    @Schema(description = "第几页")
    private int pageIndex;

    @Schema(description = "每页条数")
    private int pageSize;
}
