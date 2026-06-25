package io.github.openground.land.job;

import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.base.utils.SpringUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.core.TaskDispatchServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 批量导数任务
 * <p>
 * 参数：jobConfigJsonFiles（任务配置文件路径，多个用逗号分隔，必填）、
 * ignoreException（是否忽略异常，Y/N，可选）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Slf4j
@Service
public class BatchFileImportJob extends JobEngine {

    static final String EXE_MODE_BATCH = "batch";
    static final String EXE_MODE = "executeMode";

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        log.info("批量导数任务开始执行");
        Date start = new Date();
        JobOut out = new JobOut();
        List<Future<JobOut>> futures = new ArrayList<>();
        ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();

        String jobConfigJsonFiles = CommonUtil.getStringValueFromHashMap(param, "jobConfigJsonFiles");
        String ignoreException = CommonUtil.getStringValueFromHashMap(param, "ignoreException");

        if (StrUtil.isEmpty(jobConfigJsonFiles)) {
            log.error("任务配置异常，未正确配置任务参数jobConfigJsonFiles");
            out.setSuccess(false);
            out.setMessage("批量导数任务，任务参数必须配置jobConfigJsonFiles");
            return out;
        }

        String[] configs = jobConfigJsonFiles.split(",");
        log.info("启动{}个子线程开始执行批量导数任务", configs.length);
        for (String fileConfig : configs) {
            log.info("开始处理{}", fileConfig);
            Map<String, Object> jobParam = new HashMap<>();
            jobParam.putAll(param);
            jobParam.put("fileConfig", fileConfig.trim());
            jobParam.put(EXE_MODE, EXE_MODE_BATCH);
            futures.add(executor.submit(new JobCallable(jobParam)));
        }

        String message = "";
        int succNum = 0, failNum = 0, i = 0;
        for (Future<JobOut> future : futures) {
            i++;
            JobOut o = future.get();
            message += i + ". " + o.getMessage() + "\n";
            if (o.getSuccess()) {
                succNum++;
            } else {
                failNum++;
            }
        }

        if ("Y".equals(ignoreException) && failNum > 0) {
            out.setSuccess(false);
        } else {
            out.setSuccess(true);
        }
        out.setMessage("执行完成，成功:" + succNum + "个，失败:" + failNum + "个。\n" + message);
        log.info("任务执行结束，耗时:{}秒", (new Date().getTime() - start.getTime()) / 1000);
        return out;
    }

    /**
     * 子任务执行线程
     */
    public static class JobCallable implements Callable<JobOut> {

        private final Map<String, Object> param;

        public JobCallable(Map<String, Object> param) {
            this.param = param;
        }

        @Override
        public JobOut call() throws Exception {
            JobEngine job = SpringUtil.getBean("fileImportJob");
            return job.execute(param);
        }
    }

}
