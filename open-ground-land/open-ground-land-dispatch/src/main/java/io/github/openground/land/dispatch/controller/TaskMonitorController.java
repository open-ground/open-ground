package io.github.openground.land.dispatch.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.TaskMonitorRequest;
import io.github.openground.land.service.TaskCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务监控控制器
 * <p>统一入口 /task/taskcenter/*</p>
 *
 * @author jack.zhang
 * @since 2026-07-16
 */
@Slf4j
@Tag(name = "任务监控")
@RestController
@RequestMapping("/task/taskcenter")
public class TaskMonitorController {

    @Autowired
    private TaskCenterService taskCenterService;

    @Operation(summary = "获取 CPS 服务列表")
    @PostMapping("/getCpsServiceList")
    public CommonResult<?> getCpsServiceList(@RequestBody TaskMonitorRequest request) {
        return taskCenterService.getCpsServiceList(request);
    }

    @Operation(summary = "线程池监控")
    @PostMapping("/threadPoolMonitor")
    public CommonResult<?> threadPoolMonitor(@RequestBody TaskMonitorRequest request) {
        return taskCenterService.threadPoolMonitor(request);
    }

    @Operation(summary = "任务仪表盘")
    @PostMapping("/taskDashboard")
    public CommonResult<?> taskDashboard(@RequestBody TaskMonitorRequest request) {
        return taskCenterService.taskDashboard(request);
    }
}
