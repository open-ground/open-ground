package io.github.openground.land.dispatch.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.TaskDispatchParamRequest;
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
 * 任务参数控制器
 * <p>统一入口 /land/taskcenter/param/*，所有操作直接本地执行（共享 land 数据源）</p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Tag(name = "任务参数")
@RestController
@RequestMapping("/task/taskcenter/param")
public class TaskDispatchParamController {

    @Autowired
    private TaskCenterService taskCenterService;

    @Operation(summary = "参数列表查询")
    @PostMapping("/query")
    public CommonResult<?> query(@Valid @RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.queryParams(request);
    }

    @Operation(summary = "新增参数")
    @PostMapping("/add")
    public CommonResult<?> add(@Valid @RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.addParam(request);
    }

    @Operation(summary = "修改参数")
    @PostMapping("/update")
    public CommonResult<?> update(@Valid @RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.updateParam(request);
    }

    @Operation(summary = "删除参数")
    @PostMapping("/delete")
    public CommonResult<?> delete(@Valid @RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.deleteParam(request);
    }

    @Operation(summary = "参数选项查询（参数列表 + 任务列表）")
    @PostMapping("/queryOptions")
    public CommonResult<?> queryOptions(@Valid @RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.queryParamOptions(request);
    }

    @Operation(summary = "查询调度组列表")
    @PostMapping("/queryCpsGroup")
    public CommonResult<?> queryCpsGroup(@RequestBody TaskDispatchParamRequest request) {
        return taskCenterService.queryCpsGroup(request);
    }
}
