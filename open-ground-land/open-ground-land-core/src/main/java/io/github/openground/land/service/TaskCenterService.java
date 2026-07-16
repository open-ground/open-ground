package io.github.openground.land.service;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.TaskCenterRequest;
import io.github.openground.land.api.dto.TaskDispatchParamRequest;
import io.github.openground.land.api.dto.TaskSegmentRequest;

/**
 * 任务中心服务接口
 * <p>提供任务中心的核心业务操作</p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
public interface TaskCenterService {

    // ==================== 任务配置 ====================

    /** 任务列表查询 */
    CommonResult<?> query(TaskCenterRequest request);

    /** 新增任务 */
    CommonResult<?> add(TaskCenterRequest request);

    /** 修改任务 */
    CommonResult<?> update(TaskCenterRequest request);

    /** 批量修改任务 */
    CommonResult<?> batchUpdate(TaskCenterRequest request);

    /** 删除任务 */
    CommonResult<?> delete(TaskCenterRequest request);

    /** 查询主机列表 */
    CommonResult<?> queryHostList(TaskCenterRequest request);

    /** 引擎启停操作 */
    CommonResult<?> onOrOffEngine(TaskCenterRequest request);

    /** 查看执行日志列表 */
    CommonResult<?> queryExeLogList(TaskCenterRequest request);

    /** 查询作业执行信息列表 */
    CommonResult<?> queryJobExeLogList(TaskCenterRequest request);

    /** 修改执行计划 */
    CommonResult<?> updateTaskExeLog(TaskCenterRequest request);

    /** 手动执行任务 */
    CommonResult<?> executeJob(TaskCenterRequest request);

    /** 手工初始化作业 */
    CommonResult<?> initJob(TaskCenterRequest request);

    /** 手工执行任务计划 */
    CommonResult<?> exeJobPlan(TaskCenterRequest request);

    /** 手动执行任务（同步返回结果） */
    CommonResult<?> executeJobSync(TaskCenterRequest request);

    // ==================== 任务参数 ====================

    /** 参数列表查询 */
    CommonResult<?> queryParams(TaskDispatchParamRequest request);

    /** 新增参数 */
    CommonResult<?> addParam(TaskDispatchParamRequest request);

    /** 修改参数 */
    CommonResult<?> updateParam(TaskDispatchParamRequest request);

    /** 删除参数 */
    CommonResult<?> deleteParam(TaskDispatchParamRequest request);

    /** 参数选项查询（参数列表 + 任务列表） */
    CommonResult<?> queryParamOptions(TaskDispatchParamRequest request);

    /** 查询调度组列表 */
    CommonResult<?> queryCpsGroup(TaskDispatchParamRequest request);

    // ==================== 分段任务 ====================

    /** 执行分段任务 */
    CommonResult<?> executeSegment(TaskSegmentRequest request);

    /** 查询分段执行信息 */
    CommonResult<?> querySegment(TaskSegmentRequest request);

    /** 清理分段上下文 */
    CommonResult<?> cleanSegmentContext(TaskSegmentRequest request);

    // ==================== 任务监控 ====================

    /** 获取 CPS 服务列表 */
    CommonResult<?> getCpsServiceList(io.github.openground.land.api.dto.TaskMonitorRequest request);

    /** 线程池监控 */
    CommonResult<?> threadPoolMonitor(io.github.openground.land.api.dto.TaskMonitorRequest request);

    /** 任务仪表盘 */
    CommonResult<?> taskDashboard(io.github.openground.land.api.dto.TaskMonitorRequest request);
}
