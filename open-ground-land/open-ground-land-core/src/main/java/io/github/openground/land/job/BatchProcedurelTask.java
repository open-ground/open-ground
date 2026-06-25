package io.github.openground.land.job;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.datasource.JdbcComponent;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * 批量存储过程调用任务 — 并发执行多个存储过程
 * <p>
 * 参数：procNames（过程名，逗号分隔）、sysEodDate（跑批日期）、dsName（数据源名，可选）、taskPlanId（执行计划ID）
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class BatchProcedurelTask extends JobEngine {

    @Autowired
    private TaskDispatchConfigMapper configMapper;

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    @Autowired
    @Qualifier("landTaskExecutor")
    protected AsyncTaskExecutor stepExecutor;

    @Autowired
    private JdbcComponent jdbcComponent;

    @Override
    public JobOut execute(Map<String, Object> param) throws Exception {
        JobOut out = new JobOut();
        Date start = new Date();
        log.info("任务执行start");
        try {
            String procNames;
            if (param.containsKey("subTaskId")) {
                procNames = CommonUtil.getStringValueFromHashMap(param, "subTaskId");
            } else {
                procNames = CommonUtil.getStringValueFromHashMap(param, "procNames");
            }
            if (StrUtil.isBlank(procNames)) {
                throw new IllegalArgumentException("procNames 参数不能为空");
            }
            String[] names = procNames.split(",");
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (String procName : names) {
                log.info("开始处理{}", procName);
                Map<String, Object> jobParam = new HashMap<>();
                jobParam.putAll(param);
                jobParam.put("procName", procName);
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
            log.info("任务执行成功，耗时[{}]毫秒", new Date().getTime() - start.getTime());
        } catch (Exception e) {
            log.error("任务执行失败", e);
            out.setSuccess(false);
            out.setMessage("执行失败: " + ExceptionUtil.stacktraceToString(e, 1500));
        }
        return out;
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
            String procName = CommonUtil.getStringValueFromHashMap(param, "procName");
            String eodDate = CommonUtil.getStringValueFromHashMap(param, "sysEodDate");
            String taskPlanId = CommonUtil.getStringValueFromHashMap(param, "taskPlanId");
            out.put("subTaskId", procName);
            out.put("taskPlanId", taskPlanId);
            String retCode = "";
            String retMsg = "";
            try {
                if (param.containsKey("dsName")) {
                    String dsName = CommonUtil.getStringValueFromHashMap(param, "dsName");
                    String dbType = jdbcComponent.getDbType(dsName);
                    String sql = JdbcComponent.getProcSql(dbType, procName, eodDate);
                    Map<String, Object> result = jdbcComponent.execProcdureSql(dsName, sql);
                    retCode = (String) result.get("retCode");
                    retMsg = (String) result.get("retMsg");
                } else {
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("procName", procName);
                    queryMap.put("eodDate", eodDate);
                    configMapper.callProcedure(queryMap);
                    retCode = (String) queryMap.get("retCode");
                    retMsg = (String) queryMap.get("retMsg");
                }
                if ("0000".equals(retCode) || "0".equals(retCode) || "S".equals(retCode)) {
                    out.put("success", true);
                } else {
                    out.put("success", false);
                }
                out.put("message", "存储过程[" + procName + "]执行完成: retCode=" + retCode + ", retMsg=" + retMsg);
            } catch (Exception e) {
                log.error("存储过程[{}]执行失败", procName, e);
                out.put("success", false);
                String message = JdbcComponent.getMessage(ExceptionUtil.stacktraceToString(e), procName);
                out.put("message", "存储过程[" + procName + "]执行失败:" + message + "\n" + ExceptionUtil.stacktraceToString(e, 1500));
            }
            out.put("exeStatus", (boolean) out.get("success") ? "S" : "F");
            log.info("存储过程[{}]执行完成，耗时[{}]毫秒", procName, new Date().getTime() - start.getTime());
            return out;
        }
    }

}
