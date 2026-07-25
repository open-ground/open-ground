package io.github.openground.land.dmp.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.common.security.SecurityContextHolder;
import io.github.openground.land.api.dto.DataExchangeConfigDTO;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import io.github.openground.land.dmp.entity.TaskDataExchangeLog;
import io.github.openground.land.dmp.entity.TaskType;
import io.github.openground.land.dmp.executor.DatePathResolver;
import io.github.openground.land.dmp.executor.DbToDbExecutor;
import io.github.openground.land.dmp.executor.DbToFileExecutor;
import io.github.openground.land.dmp.executor.FileToDbExecutor;
import io.github.openground.land.dmp.mapper.TaskDataExchangeConfigMapper;
import io.github.openground.land.dmp.mapper.TaskDataExchangeLogMapper;
import io.github.openground.land.dmp.service.TaskDataExchangeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据交换配置控制器
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@Slf4j
@Tag(name = "数据交换配置")
@RestController
@RequestMapping("/task/exchange/config")
public class TaskExchangeConfigController {

    @Autowired
    private TaskDataExchangeConfigService configService;

    @Autowired
    private io.github.openground.land.dmp.service.LineageService lineageService;

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

    @Autowired
    private TaskDataExchangeLogMapper logMapper;

    @Operation(summary = "配置列表查询")
    @PostMapping("/list")
    public CommonResult<?> list(@RequestBody DataExchangeConfigDTO request) {
        return configService.list(request);
    }

    @Operation(summary = "配置详情")
    @PostMapping("/get")
    public CommonResult<?> get(@RequestBody DataExchangeConfigDTO request) {
        return configService.getById(request.getId());
    }

    @Operation(summary = "保存配置（新增/更新）")
    @PostMapping("/save")
    public CommonResult<?> save(@Valid @RequestBody DataExchangeConfigDTO request) {
        return configService.save(request);
    }

    @Operation(summary = "删除配置")
    @PostMapping("/delete")
    public CommonResult<?> delete(@RequestBody DataExchangeConfigDTO request) {
        return configService.delete(request.getId());
    }

    /**
     * 手动执行数据交换任务
     *
     * @since 1.0.5
     */
    @Operation(summary = "手动执行数据交换任务")
    @PostMapping("/execute")
    public CommonResult<?> execute(@RequestBody DataExchangeConfigDTO request) {
        TaskDataExchangeConfig config = configMapper.selectById(request.getId());
        if (config == null) {
            return CommonResult.error("1000", "配置不存在");
        }
        String taskType = config.getTaskType();
        String executor = SecurityContextHolder.getCurrentUsername();
        log.info("手动执行任务: type={}, configId={}, executor={}", taskType, config.getId(), executor);

        // 记录执行人信息到配置
        config.setUpdateBy(executor);
        config.setUpdateTime(new Date());
        configMapper.updateById(config);

        // 创建执行日志（初始状态）
        TaskDataExchangeLog execLog = new TaskDataExchangeLog();
        execLog.setId(KeyGenerator.getInternalKey());
        execLog.setConfigId(config.getId());
        execLog.setTaskType(taskType);
        execLog.setStartTime(new Date());
        execLog.setRunStatus("RUNNING");
        execLog.setCreateBy(executor);
        execLog.setCreateTime(new Date());
        logMapper.insert(execLog);

        // 异步执行，避免大任务请求超时
        final TaskDataExchangeLog logRef = execLog;
        landTaskExecutor.submit(() -> executeTask(logRef, config, taskType));

        Map<String, Object> result = new HashMap<>();
        result.put("logId", execLog.getId());
        result.put("status", "RUNNING");
        return CommonResult.success(result);
    }

    private void executeTask(TaskDataExchangeLog execLog, TaskDataExchangeConfig config, String taskType) {
        try {
            if (TaskType.FILE_TO_DB.matches(taskType)) {
                FileToDbExecutor.ExecuteResult execResult = fileToDbExecutor.execute(config);
                logMapper.updateById(completedLog(execLog, execResult.getErrorRows() > 0 ? "PARTIAL" : "SUCCESS",
                        execResult.getSuccessRows() + execResult.getErrorRows(),
                        "成功 " + execResult.getSuccessRows() + " 行"
                                + (execResult.getErrorRows() > 0 ? "，失败 " + execResult.getErrorRows() + " 行" : "")
                                + (execResult.getErrorLogPath() != null ? "。错误文件：" + execResult.getErrorLogPath() : "")));
            } else if (TaskType.DB_TO_FILE.matches(taskType)) {
                int rows = dbToFileExecutor.execute(config);
                logMapper.updateById(completedLog(execLog, "SUCCESS", rows, null));
            } else if (TaskType.DB_TO_DB.matches(taskType)) {
                DbToDbExecutor.ExecuteResult execResult = dbToDbExecutor.execute(config);
                logMapper.updateById(completedLog(execLog, execResult.getErrorRows() > 0 ? "PARTIAL" : "SUCCESS",
                        execResult.getSuccessRows() + execResult.getErrorRows(),
                        "成功 " + execResult.getSuccessRows() + " 行"
                                + (execResult.getErrorRows() > 0 ? "，失败 " + execResult.getErrorRows() + " 行" : "")
                                + (execResult.getErrorLogPath() != null ? "。错误文件：" + execResult.getErrorLogPath() : "")));
            } else {
                logMapper.updateById(completedLog(execLog, "FAIL", 0, "不支持的任务类型: " + taskType));
            }
        } catch (Exception e) {
            log.error("任务执行异常: configId={}", execLog.getConfigId(), e);
            logMapper.updateById(completedLog(execLog, "FAIL", 0, e.getMessage()));
        }
    }

    @Operation(summary = "批量执行任务")
    @PostMapping("/batchExecute")
    public CommonResult<?> batchExecute(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) params.get("ids");
        if (ids == null || ids.isEmpty()) {
            return CommonResult.error("1000", "请选择要执行的任务");
        }
        String executor = SecurityContextHolder.getCurrentUsername();
        for (Integer id : ids) {
            TaskDataExchangeConfig config = configMapper.selectById(id.longValue());
            if (config == null) continue;
            String taskType = config.getTaskType();
            // 创建执行日志
            TaskDataExchangeLog execLog = new TaskDataExchangeLog();
            execLog.setId(KeyGenerator.getInternalKey());
            execLog.setConfigId(config.getId());
            execLog.setTaskType(taskType);
            execLog.setStartTime(new Date());
            execLog.setRunStatus("RUNNING");
            execLog.setCreateBy(executor);
            execLog.setCreateTime(new Date());
            logMapper.insert(execLog);
            // 异步提交
            final TaskDataExchangeLog logRef = execLog;
            landTaskExecutor.submit(() -> executeTask(logRef, config, taskType));
        }
        return CommonResult.success("已提交 " + ids.size() + " 个任务");
    }

    @Operation(summary = "下载异常文件")
    @PostMapping("/errorFile")
    public void errorFile(@RequestBody DataExchangeConfigDTO request, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        TaskDataExchangeConfig config = configMapper.selectById(request.getId());
        if (config == null) {
            response.sendError(404, "配置不存在");
            return;
        }
        String sourceFilePath = DatePathResolver.resolve(config.getSourceFilePath());
        String errorFilePath = sourceFilePath + ".err";
        java.io.File file = new java.io.File(errorFilePath);
        if (!file.exists()) {
            response.sendError(404, "异常文件不存在");
            return;
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setContentLengthLong(file.length());
        try (java.io.InputStream is = new java.io.FileInputStream(file);
             java.io.OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
        }
    }

    @Operation(summary = "执行日志查询")
    @PostMapping("/logList")
    public CommonResult<?> logList(@RequestBody DataExchangeConfigDTO request) {
        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<TaskDataExchangeLog> list = logMapper.selectList(request.getId(), null, null);
        PageInfo<TaskDataExchangeLog> page = new PageInfo<>(list);

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getList());
        result.put("total", page.getTotal());
        return CommonResult.success(result);
    }

    private TaskDataExchangeLog completedLog(TaskDataExchangeLog execLog, String status, int rows, String errorMsg) {
        Date now = new Date();
        execLog.setEndTime(now);
        execLog.setDurationSeconds((int) ((now.getTime() - execLog.getStartTime().getTime()) / 1000));
        execLog.setRunStatus(status);
        execLog.setRowCount(rows);
        execLog.setErrorMsg(errorMsg);
        return execLog;
    }

    @Operation(summary = "数据数据流图")
    @PostMapping("/lineage")
    public CommonResult<?> lineage() {
        return lineageService.getLineage();
    }
}
