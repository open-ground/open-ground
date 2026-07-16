package io.github.openground.land.job;

import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

/**
 * 测试任务 — 用于调试和验证
 * <p>
 * 参数：filePath、fileName、sysRunDate、sysEodDate
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
@Transactional
public class TestJob extends JobEngine {

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    @Override
    public JobOut execute(Map<String, Object> param) {
        JobOut out = new JobOut();
        Date start = new Date();
        log.info("任务开始执行");

        try {
            TaskDispatchExeLogDomain po = new TaskDispatchExeLogDomain();
            po.setId("2021061600003813");
            po.setJobId("test");

//            exeLogMapper.updateById(po);

            log.info("任务执行成功,耗时" + (new Date().getTime() - start.getTime()) + "毫秒" );
            out.setSuccess(true);
            out.setMessage("任务执行成功");
        } catch (Exception e) {
            log.error("任务执行失败{}", e);
            out.setSuccess(false);
            out.setMessage("执行失败:" + (e.getMessage() != null && e.getMessage().length() > 1000 ? e.getMessage().substring(0, 1000) : e.getMessage()));
        }
        return out;
    }
}
