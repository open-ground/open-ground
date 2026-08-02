package io.github.openground.land.dispatch.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.log.annotation.OptLog;
import io.github.openground.common.log.enums.OptType;
import io.github.openground.land.api.dto.TaskCenterRequest;
import io.github.openground.land.api.executor.RemoteTaskExecutor;
import io.github.openground.land.service.TaskCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务中心控制器
 * <p>统一入口 /land/taskcenter/*，与旧路径主体保持一致</p>
 * <p>
 * 数据类操作（CRUD/查询/状态更新）直接注入 TaskCenterService 本地执行，
 * 执行类操作（executeJob/initJob/exeJobPlan）通过 RemoteTaskExecutor 转发到目标 cpsGroup 实例。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Tag(name = "任务中心")
@RestController
@RequestMapping("/task/taskcenter")
public class TaskCenterController {

    @Autowired
    private TaskCenterService taskCenterService;

    @Autowired(required = false)
    private RemoteTaskExecutor remoteTaskExecutor;

    // ==================== 数据类操作（本地直连共享中心库）====================

    @Operation(summary = "任务列表查询")
    @OptLog(optType = OptType.OTHER, optRemark = "任务列表查询")
    @PostMapping("/query")
    public CommonResult<?> query(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.query(request);
    }

    @Operation(summary = "新增任务")
    @OptLog(optType = OptType.INSERT, optRemark = "新增任务")
    @PostMapping("/add")
    public CommonResult<?> add(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.add(request);
    }

    @Operation(summary = "修改任务")
    @OptLog(optType = OptType.UPDATE, optRemark = "修改任务")
    @PostMapping("/update")
    public CommonResult<?> update(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.update(request);
    }

    @Operation(summary = "删除任务")
    @OptLog(optType = OptType.DELETE, optRemark = "删除任务")
    @PostMapping("/delete")
    public CommonResult<?> delete(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.delete(request);
    }

    @Operation(summary = "批量修改任务")
    @OptLog(optType = OptType.UPDATE, optRemark = "批量修改任务")
    @PostMapping("/batchUpdate")
    public CommonResult<?> batchUpdate(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.batchUpdate(request);
    }

    @Operation(summary = "查询主机列表")
    @OptLog(optType = OptType.OTHER, optRemark = "查询主机列表")
    @PostMapping("/queryhostlist")
    public CommonResult<?> queryHostList(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.queryHostList(request);
    }

    @Operation(summary = "引擎启停操作")
    @OptLog(optType = OptType.UPDATE, optRemark = "引擎启停操作")
    @PostMapping("/engine")
    public CommonResult<?> onOrOffEngine(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.onOrOffEngine(request);
    }

    @Operation(summary = "查看执行日志列表")
    @OptLog(optType = OptType.OTHER, optRemark = "查看执行日志列表")
    @PostMapping("/queryExeLogList")
    public CommonResult<?> queryExeLogList(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.queryExeLogList(request);
    }

    @Operation(summary = "查询作业执行信息列表")
    @OptLog(optType = OptType.OTHER, optRemark = "查询作业执行信息列表")
    @PostMapping("/queryJobExeLogList")
    public CommonResult<?> queryJobExeLogList(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.queryJobExeLogList(request);
    }

    @Operation(summary = "修改执行计划")
    @OptLog(optType = OptType.UPDATE, optRemark = "修改执行计划")
    @PostMapping("/updateTaskExeLog")
    public CommonResult<?> updateTaskExeLog(@Valid @RequestBody TaskCenterRequest request) {
        return taskCenterService.updateTaskExeLog(request);
    }

    // ==================== 执行类操作（通过 RemoteTaskExecutor 转发到目标实例）====================

    @Operation(summary = "手动执行任务")
    @OptLog(optType = OptType.OTHER, optRemark = "手动执行任务")
    @PostMapping("/executeJob")
    public CommonResult<?> executeJob(@Valid @RequestBody TaskCenterRequest request) {
        String cpsGroup = request.getCpsGroup();
        if (cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null) {
            // 需要转发到目标 cpsGroup 实例
            return remoteTaskExecutor.execute(cpsGroup, "/executeJob", request);
        }
        // 合并部署或无 cpsGroup 时本地执行
        return taskCenterService.executeJob(request);
    }

    @Operation(summary = "手工初始化作业")
    @OptLog(optType = OptType.OTHER, optRemark = "手工初始化作业")
    @PostMapping("/initJob")
    public CommonResult<?> initJob(@Valid @RequestBody TaskCenterRequest request) {
        String cpsGroup = request.getCpsGroup();
        if (cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null) {
            return remoteTaskExecutor.execute(cpsGroup, "/initJob", request);
        }
        return taskCenterService.initJob(request);
    }

    @Operation(summary = "手工执行任务计划")
    @OptLog(optType = OptType.OTHER, optRemark = "手工执行任务计划")
    @PostMapping("/exeJobPlan")
    public CommonResult<?> exeJobPlan(@Valid @RequestBody TaskCenterRequest request) {
        String cpsGroup = request.getCpsGroup();
        if (cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null) {
            return remoteTaskExecutor.execute(cpsGroup, "/exeJobPlan", request);
        }
        return taskCenterService.exeJobPlan(request);
    }

    @Operation(summary = "手动执行任务（同步返回结果）")
    @OptLog(optType = OptType.OTHER, optRemark = "手动执行任务（同步）")
    @PostMapping("/executeJobSync")
    public CommonResult<?> executeJobSync(@Valid @RequestBody TaskCenterRequest request) {
        String cpsGroup = request.getCpsGroup();
        if (cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null) {
            return remoteTaskExecutor.execute(cpsGroup, "/executeJobSync", request);
        }
        return taskCenterService.executeJobSync(request);
    }
}
