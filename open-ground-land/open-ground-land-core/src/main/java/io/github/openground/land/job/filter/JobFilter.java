package io.github.openground.land.job.filter;

import java.util.Map;

/**
 * Job 过滤器接口
 * <p>在执行 Job 前对数据进行过滤/校验，返回 true 表示接受该数据。</p>
 *
 * @param <T> 数据类型
 * @author jack.zhang
 * @since 2026-06-25
 */
public interface JobFilter<T> {

    /**
     * 过滤数据
     *
     * @param context 上下文参数
     * @param data    待检查的数据
     * @return true 接受 / false 拒绝
     */
    boolean filter(Map<String, Object> context, T data);
}
