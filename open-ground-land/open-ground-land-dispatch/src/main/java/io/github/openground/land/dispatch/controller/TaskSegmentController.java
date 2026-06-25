package io.github.openground.land.dispatch.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.TaskSegmentRequest;
import io.github.openground.land.api.executor.RemoteTaskExecutor;
import io.github.openground.land.service.TaskCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分段任务控制器
 * <p>统一入口 /land/taskcenter/segment/*</p>
 * <p>
 * query 和 cleanContext 为数据类操作，直接本地执行；
 * execute 为执行类操作，通过 RemoteTaskExecutor 转发到目标 cpsGroup 实例。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Tag(name = "分段任务")
@RestController
@RequestMapping("/land/taskcenter/segment")
public class TaskSegmentController {

    @Autowired
    private TaskCenterService taskCenterService;

    @Autowired(required = false)
    private RemoteTaskExecutor remoteTaskExecutor;

    @Operation(summary = "执行分段任务")
    @PostMapping("/execute")
    public CommonResult<?> execute(@Valid @RequestBody TaskSegmentRequest request) {
        String cpsGroup = request.getCpsGroup();
        if (cpsGroup != null && !cpsGroup.isEmpty() && remoteTaskExecutor != null) {
            return remoteTaskExecutor.execute(cpsGroup, "/segment/execute", request);
        }
        return taskCenterService.executeSegment(request);
    }

    @Operation(summary = "查询分段执行信息")
    @PostMapping("/query")
    public CommonResult<?> query(@Valid @RequestBody TaskSegmentRequest request) {
        return taskCenterService.querySegment(request);
    }

    @Operation(summary = "清理分段上下文")
    @PostMapping("/cleanContext")
    public CommonResult<?> cleanContext(@Valid @RequestBody TaskSegmentRequest request) {
        return taskCenterService.cleanSegmentContext(request);
    }
}
