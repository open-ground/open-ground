package io.github.openground.land.api.executor;

import io.github.openground.base.dto.CommonResult;

/**
 * 远程任务执行器 — 将执行类操作转发到目标 cpsGroup 实例
 * <p>
 * 仅用于 executeJob / initJob / exeJobPlan / executeSegment 等需要在目标实例上
 * 运行业务 Job 或启动调度线程的操作。数据类操作（CRUD/查询）直接本地执行，无需此接口。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
public interface RemoteTaskExecutor {

    /**
     * 将执行请求转发到指定 cpsGroup 的可用实例
     *
     * @param cpsGroup 目标调度组（服务名）
     * @param action   请求路径，如 /executeJob
     * @param request  请求体
     * @return 执行结果
     */
    CommonResult<?> execute(String cpsGroup, String action, Object request);
}
