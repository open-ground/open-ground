package io.github.openground.land.core;

import cn.hutool.core.exceptions.ExceptionUtil;
import io.github.openground.base.utils.SpringUtil;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 手工异步执行任务线程
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Slf4j
public class ExecuteJobThread extends Thread {

    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private TaskDispatchConfigDomain task;
    private String taskLogId;
    private String sysEodDate;
    private String exeParams;

    public ExecuteJobThread(TaskDispatchConfigMapper configMapper,
                           TaskDispatchExeLogMapper exeLogMapper,
                           String taskId, String sysEodDate) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.sysEodDate = sysEodDate;
        task = configMapper.selectTaskDispatchConfigByPK(taskId);
        this.saveExeLog();
    }

    public ExecuteJobThread(TaskDispatchConfigMapper configMapper,
                           TaskDispatchExeLogMapper exeLogMapper,
                           String taskId, String sysEodDate, String exeParams) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.sysEodDate = sysEodDate;
        this.exeParams = exeParams;
        task = configMapper.selectTaskDispatchConfigByPK(taskId);
        this.saveExeLog();
    }

    public ExecuteJobThread(TaskDispatchConfigMapper configMapper,
                           TaskDispatchExeLogMapper exeLogMapper,
                           String taskId, String sysEodDate, String exeParams, String taskLogId) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.sysEodDate = sysEodDate;
        this.exeParams = exeParams;
        task = configMapper.selectTaskDispatchConfigByPK(taskId);
        this.taskLogId = taskLogId;
        this.saveExeLog();
    }

    @Override
    public void run() {
        exeJob();
    }

    public JobOut exeJob() {
        Map<String, Object> paramsMap = new HashMap<>();

        String params = task.getParams();
        if (!StringUtils.isEmpty(params)) {
            try {
                TaskDispatchServiceUtil.addParams(params, paramsMap);
            } catch (Exception e) {
                log.error("处理参数异常", e);
            }
        }
        if (!StringUtils.isEmpty(this.exeParams)) {
            try {
                TaskDispatchServiceUtil.addParams(this.exeParams, paramsMap);
            } catch (Exception e) {
                log.error("处理参数异常", e);
            }
        }
        if (!StringUtils.isEmpty(sysEodDate)) {
            paramsMap.put("sysEodDate", sysEodDate);
        }
        if (!paramsMap.containsKey("sysEodDate")) {
            paramsMap.put("sysEodDate", TaskDateUtil.getSysEodDate(task.getCpsGroup()));
        }
        paramsMap.put("isAutoExe", false);
        paramsMap.put("taskPlanId", taskLogId);
        paramsMap.put("taskConfig", task);
        JobOut out = new JobOut();
        MDC.put("taskId", task.getTaskId());
        try {
            JobEngine job = SpringUtil.getBean(task.getTaskMethod());
            out = job.execute(paramsMap);
        } catch (Exception e) {
            log.error("任务执行异常", e);
            String errorMsg = ExceptionUtil.stacktraceToString(e, 500);
            out.setSuccess(false);
            out.setMessage(errorMsg);
        }
        MDC.remove("taskId");
        TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
        updateLog.setId(this.taskLogId);
        updateLog.setExeStatus(out.getSuccess() == true ? "S" : "F");
        updateLog.setErrInfo(io.github.openground.base.utils.StringUtils.subByBytes(out.getMessage(), 1000, "utf-8"));
        updateLog.setExeEndTime(TaskDateUtil.getMachingCurrentTime());
        Map<String, Object> updateParam = new HashMap<>();
        updateParam.put("log", updateLog);
        exeLogMapper.updateTaskDispatchExeLog(updateParam);
        return out;
    }

    private void saveExeLog() {
        TaskDispatchExeLogDomain taskLog = new TaskDispatchExeLogDomain();
        if (ObjectUtils.isEmpty(this.taskLogId)) {
            this.taskLogId = KeyGenerator.getBusinessKey("TASK_LOG");
        }
        taskLog.setId(this.taskLogId);
        taskLog.setEodDate(sysEodDate);
        taskLog.setTaskId(task.getTaskId());
        taskLog.setJobId(task.getTaskId());
        taskLog.setPlanStartTime("手动执行");
        taskLog.setExeStartTime(TaskDateUtil.getMachingCurrentTime());
        taskLog.setExeEndTime("");
        taskLog.setExeStatus("R");
        taskLog.setErrInfo("");
        taskLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
        taskLog.setMtTime(TaskDateUtil.getMachingCurrentTime());
        taskLog.setBatchNo(task.getBatchNo() + "");
        taskLog.setFileName("");
        taskLog.setExtend1("");
        taskLog.setExtend2("N");
        taskLog.setCompany(task.getCompany());
        exeLogMapper.insertTaskDispatchExeLog(taskLog);
    }
}
