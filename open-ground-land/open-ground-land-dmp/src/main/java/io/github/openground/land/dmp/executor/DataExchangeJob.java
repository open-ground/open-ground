package io.github.openground.land.dmp.executor;

import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import io.github.openground.land.dmp.entity.TaskType;
import io.github.openground.land.dmp.mapper.TaskDataExchangeConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据交换 Job
 * <p>继承 JobEngine，由 land 调度框架统一调度执行</p>
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@Slf4j
@Component("dataExchangeJob")
public class DataExchangeJob extends JobEngine {

    @Autowired
    private TaskDataExchangeConfigMapper configMapper;

    @Autowired
    private FileToDbExecutor fileToDbExecutor;

    @Autowired
    private DbToFileExecutor dbToFileExecutor;

    @Autowired
    private DbToDbExecutor dbToDbExecutor;

    @Autowired
    @Qualifier("landTaskExecutor")
    private AsyncTaskExecutor landTaskExecutor;

    @Override
    public JobOut execute(Map<String, Object> params) {
        JobOut out = new JobOut();
        out.setSuccess(false);

        try {
            // 批量执行：configIds 逗号分隔
            Object configIdsObj = params.get("configIds");
            if (configIdsObj != null) {
                String[] ids = configIdsObj.toString().split(",");
                for (String idStr : ids) {
                    Long configId = Long.valueOf(idStr.trim());
                    landTaskExecutor.submit(() -> executeSingle(configId));
                }
                out.setSuccess(true);
                out.setMessage("已提交 " + ids.length + " 个任务");
                return out;
            }

            // 单任务执行（原逻辑）
            Object configIdObj = params.get("configId");
            if (configIdObj == null) {
                out.setMessage("参数 configId 或 configIds 不能为空");
                return out;
            }

            Long configId = Long.valueOf(configIdObj.toString());
            TaskDataExchangeConfig config = configMapper.selectById(configId);
            if (config == null) {
                out.setMessage("配置不存在: " + configId);
                return out;
            }

            String taskType = config.getTaskType();
            log.info("数据交换任务开始: type={}, configId={}", taskType, configId);

            if (TaskType.FILE_TO_DB.matches(taskType)) {
                FileToDbExecutor.ExecuteResult result = fileToDbExecutor.execute(config);
                out.setSuccess(result.getErrorRows() == 0);
                out.setMessage("文件入库完成，共处理 " + result.getSuccessRows() + " 行"
                        + (result.getErrorRows() > 0 ? "，失败=" + result.getErrorRows() + " 行" : ""));
            } else if (TaskType.DB_TO_FILE.matches(taskType)) {
                int rows = dbToFileExecutor.execute(config);
                out.setSuccess(true);
                out.setMessage("库导出文件完成，共导出 " + rows + " 行");
            } else if (TaskType.DB_TO_DB.matches(taskType)) {
                DbToDbExecutor.ExecuteResult result = dbToDbExecutor.execute(config);
                out.setSuccess(result.getErrorRows() == 0);
                out.setMessage("库→库同步完成，共处理 " + result.getSuccessRows() + " 行"
                        + (result.getErrorRows() > 0 ? "，失败=" + result.getErrorRows() + " 行" : ""));
            } else {
                out.setMessage("暂不支持的任务类型: " + taskType);
            }

        } catch (Exception e) {
            log.error("数据交换任务执行异常", e);
            out.setMessage("执行失败: " + e.getMessage());
        }

        return out;
    }

    private void executeSingle(Long configId) {
        try {
            TaskDataExchangeConfig config = configMapper.selectById(configId);
            if (config == null) {
                log.warn("配置不存在: {}", configId);
                return;
            }
            String taskType = config.getTaskType();
            log.info("数据交换任务开始: type={}, configId={}", taskType, configId);

            if (TaskType.FILE_TO_DB.matches(taskType)) {
                FileToDbExecutor.ExecuteResult result = fileToDbExecutor.execute(config);
                log.info("文件入库完成: configId={}, 成功={}, 失败={}", configId, result.getSuccessRows(), result.getErrorRows());
            } else if (TaskType.DB_TO_FILE.matches(taskType)) {
                int rows = dbToFileExecutor.execute(config);
                log.info("库导出文件完成: configId={}, 行数={}", configId, rows);
            } else if (TaskType.DB_TO_DB.matches(taskType)) {
                DbToDbExecutor.ExecuteResult result = dbToDbExecutor.execute(config);
                log.info("库→库同步完成: configId={}, 成功={}, 失败={}", configId, result.getSuccessRows(), result.getErrorRows());
            } else {
                log.warn("暂不支持的任务类型: configId={}, type={}", configId, taskType);
            }
        } catch (Exception e) {
            log.error("数据交换任务执行异常: configId={}", configId, e);
        }
    }
}
