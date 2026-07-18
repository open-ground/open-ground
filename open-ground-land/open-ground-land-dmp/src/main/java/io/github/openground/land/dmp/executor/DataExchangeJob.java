package io.github.openground.land.dmp.executor;

import cn.hutool.core.util.StrUtil;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import io.github.openground.land.dmp.mapper.DmpDataExchangeConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据交换 Job
 * <p>继承 JobEngine，由 land 调度框架统一调度执行</p>
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Component("dataExchangeJob")
public class DataExchangeJob extends JobEngine {

    @Autowired
    private DmpDataExchangeConfigMapper configMapper;

    @Autowired
    private FileToDbExecutor fileToDbExecutor;

    @Override
    public JobOut execute(Map<String, Object> params) {
        JobOut out = new JobOut();
        out.setSuccess(false);

        try {
            // 从参数中获取配置ID
            Object configIdObj = params.get("configId");
            if (configIdObj == null) {
                out.setMessage("参数 configId 不能为空");
                return out;
            }

            String configId = configIdObj.toString();
            DmpDataExchangeConfig config = configMapper.selectById(configId);
            if (config == null) {
                out.setMessage("配置不存在: " + configId);
                return out;
            }

            String taskType = config.getTaskType();
            log.info("数据交换任务开始: type={}, configId={}", taskType, configId);

            if ("FILE_TO_DB".equals(taskType)) {
                int rows = fileToDbExecutor.execute(config);
                out.setSuccess(true);
                out.setMessage("文件入库完成，共处理 " + rows + " 行");
            } else {
                out.setMessage("暂不支持的任务类型: " + taskType);
            }

        } catch (Exception e) {
            log.error("数据交换任务执行异常", e);
            out.setMessage("执行失败: " + e.getMessage());
        }

        return out;
    }
}
