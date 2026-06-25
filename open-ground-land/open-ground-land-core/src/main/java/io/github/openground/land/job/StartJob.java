package io.github.openground.land.job;

import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 开始作业 — 流程起始标记
 * <p>作为批处理流程的起始节点，直接返回成功。</p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class StartJob extends JobEngine {

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        out.setSuccess(true);
        out.setMessage("开始作业执行成功");
        return out;
    }
}
