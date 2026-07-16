package io.github.openground.land.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 任务仪表盘响应 DTO
 *
 * @author jack.zhang
 * @since 2026-07-16
 */
@Data
@Schema(description = "任务仪表盘响应")
public class TaskDashboardResponse {

    @Schema(description = "任务总数")
    private int totalTask;

    @Schema(description = "启用任务数")
    private int activateTask;

    @Schema(description = "停用任务数")
    private int disableTask;

    @Schema(description = "调度总次数")
    private int schCount;

    @Schema(description = "调度成功次数")
    private int schCountSuccess;

    @Schema(description = "调度失败次数")
    private int schCountError;

    @Schema(description = "今日调度总次数")
    private int todaySchCount;

    @Schema(description = "今日调度成功次数")
    private int todaySchCountSuccess;

    @Schema(description = "今日调度失败次数")
    private int todaySchCountError;

    @Schema(description = "执行节点总数")
    private int totalNode;

    @Schema(description = "启动的节点数")
    private int activateNode;

    @Schema(description = "停用的节点数")
    private int disableNode;

    @Schema(description = "跑批日期")
    private String sysEodDate;

    @Schema(description = "调度报表信息")
    private List scheduleList;

    @Schema(description = "待执行任务列表")
    private List preTaskList;
}
