package io.github.openground.land.job;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * 批量 Shell 任务 — 并发执行多个 Shell 脚本
 * <p>
 * 参数：shellPath（脚本路径）、shellParam（脚本参数）、shellFiles（文件名，逗号分隔）、
 * bash（Shell类型）、sysEodDate（跑批日期）、taskPlanId（执行计划ID）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class BatchShellTask extends JobEngine {

    @Autowired
    @Qualifier("landTaskExecutor")
    protected AsyncTaskExecutor stepExecutor;

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        Date start = new Date();
        log.info("任务执行start");
        try {
            String shellPath = CommonUtil.getStringValueFromHashMap(param, "shellPath");
            String shellParam = CommonUtil.getStringValueFromHashMap(param, "shellParam");
            String sysEodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");

            if (!param.containsKey("bash")) {
                param.put("bash", "sh ");
            }
            String shellFiles;
            if (param.containsKey("subTaskId")) {
                shellFiles = CommonUtil.getStringValueFromHashMap(param, "subTaskId");
            } else {
                shellFiles = CommonUtil.getStringValueFromHashMap(param, "shellFiles");
            }
            if (StrUtil.isBlank(shellPath)) {
                throw new IllegalArgumentException("shellPath 参数不能为空");
            }
            if (StrUtil.isBlank(shellFiles)) {
                throw new IllegalArgumentException("shellFiles 参数不能为空");
            }
            if (StrUtil.isNotBlank(shellParam)) {
                shellParam = shellParam.replace("${sysEodDate}", sysEodDate);
                param.put("shellParam", shellParam);
            }

            String[] names = shellFiles.split(",");
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (String shellFile : names) {
                log.info("开始处理{}", shellFile);
                Map<String, Object> jobParam = new HashMap<>();
                jobParam.putAll(param);
                jobParam.put("shellFile", shellFile);
                futures.add(stepExecutor.submit(new JobCallable(jobParam)));
            }

            int succNum = 0, failNum = 0;
            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            List<Map<String, Object>> attacheds = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                Map<String, Object> o = future.get();
                attacheds.add(o);
                if ((boolean) o.get("success")) {
                    succNum++;
                } else {
                    failNum++;
                }
            }
            if (failNum > 0) {
                out.setSuccess(false);
            } else {
                out.setSuccess(true);
            }
            if (!param.containsKey("subTaskId")) {
                String taskPlanId = CommonUtil.getStringValueFromHashMap(param, "taskPlanId");
                updateLog.setId(taskPlanId);
                updateLog.getAttacheds().put("attacheds", attacheds);
                updateLog.buildAttachedStr();
                Map<String, Object> updateParam = new HashMap<>();
                updateParam.put("log", updateLog);
                exeLogMapper.updateTaskDispatchExeLog(updateParam);
            }
            out.setMessage("执行完成，成功:" + succNum + "个，失败:" + failNum + "个。\n");

            log.info("任务执行成功,耗时[{}]毫秒", new Date().getTime() - start.getTime());
        } catch (Exception e) {
            log.error("任务执行失败", e);
            out.setSuccess(false);
            out.setMessage("执行失败{}" + ExceptionUtil.stacktraceToString(e, 1500));
        }
        return out;
    }

    public String executeShell(String bash, String shellPath, String shellFile, String shellParam) throws Exception {
        if (!shellPath.endsWith(File.separator)) {
            shellPath = shellPath + File.separator;
        }
        File file = new File(shellPath + shellFile);
        if (!file.exists()) {
            throw new Exception("shell文件不存在: " + shellPath + shellFile);
        }
        StringBuilder output = null;
        String errorLine = "";
        int exitCode = 0;
        String command = "";
        try {
            command = bash + shellPath + shellFile + " " + (shellParam != null ? shellParam : "");
            log.info("执行shell命令: {}", command);
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((errorLine = errorReader.readLine()) != null) {
                output.append(errorLine).append("\n");
            }
            log.info("shell: " + " - Output: " + output);
            exitCode = process.waitFor();
        } catch (Exception e) {
            log.error("shell执行失败", e);
            throw new RuntimeException("shell command: [" + command + "] 执行失败:", e);
        }
        if (exitCode != 0) {
            log.info("shell exe error: " + " - Error Output: " + errorLine);
            throw new Exception("shell command: [" + command + "] 执行失败: Exit Code: " + exitCode + " + output + " + errorLine);
        }
        log.info("shell: " + " - Process exited with code: " + exitCode);
        return output != null ? output.toString() : "";
    }

    public class JobCallable implements Callable<Map<String, Object>> {

        Map<String, Object> param;

        public JobCallable(Map<String, Object> param) {
            this.param = param;
        }

        @Override
        public Map<String, Object> call() throws Exception {
            Date start = new Date();
            Map<String, Object> out = new HashMap<>();
            String taskPlanId = CommonUtil.getStringValueFromHashMap(param, "taskPlanId");
            String shellPath = CommonUtil.getStringValueFromHashMap(param, "shellPath");
            String shellParam = CommonUtil.getStringValueFromHashMap(param, "shellParam");
            String shellFile = CommonUtil.getStringValueFromHashMap(param, "shellFile");
            String bash = CommonUtil.getStringValueFromHashMap(param, "bash");
            out.put("subTaskId", shellFile);
            out.put("taskPlanId", taskPlanId);
            try {
                String result = executeShell(bash, shellPath, shellFile, shellParam);
                out.put("success", true);
                out.put("message", "脚本[" + shellFile + "]执行成功：" + result);
            } catch (Exception e) {
                log.error("脚本[{}]执行失败", shellFile, e);
                out.put("success", false);
                out.put("message", ExceptionUtil.stacktraceToString(e, 500));
            }
            out.put("exeStatus", (boolean) out.get("success") ? "S" : "F");
            log.info("脚本[{}]执行完成，耗时[{}]毫秒", shellFile, new Date().getTime() - start.getTime());
            return out;
        }
    }

}
