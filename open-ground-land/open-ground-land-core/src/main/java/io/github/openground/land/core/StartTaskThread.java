package io.github.openground.land.core;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.github.pagehelper.util.StringUtil;
import io.github.openground.base.utils.SpringUtil;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.entity.TaskDispatchParam;
import io.github.openground.land.common.util.MsgUtil;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.mapper.TaskDispatchParamMapper;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 调起任务线程
 *
 * @author jack.zhang
 * @version v_1.0
 * @date 2017年3月17日 下午1:45:01
 */
@Slf4j
//@Transactional(value = "dispatchTransactionManager", propagation= Propagation.REQUIRES_NEW)
public class StartTaskThread implements Runnable {

    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private String logId = "";
    private String taskId = "";
    private String fileName = "";
    private TaskDispatchExeLogDomain taskLog;

    /**
     * @param configMapper
     * @param exeLogMapper
     * @param taskPlan 要执行的任务计划
     */
    public StartTaskThread(TaskDispatchConfigMapper configMapper,
                           TaskDispatchExeLogMapper exeLogMapper,
                           TaskDispatchExeLogDomain taskPlan) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.logId = taskPlan.getId();
        this.taskId = taskPlan.getTaskId();
        this.fileName = taskPlan.getFileName();
        this.taskLog = taskPlan;
    }

    @Override
    public void run() {
        Map<String, Object> param = new HashMap<>();
        TaskDispatchConfigDomain taskInfo = null;
        try {
            taskInfo = configMapper.selectTaskDispatchConfigByPK(taskId);
            ////////////////////////////////////////////////////////////////////
            //midify by ，停用的任务也生成执行计划，但是在执行时直接返回成功
            if ("0".equals(taskInfo.getStatus())) {
                log.info("[" + taskInfo.getTaskName() + "]任务已停用，不执行直接返回成功!");
                TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                updateLog.setId(logId);
                updateLog.setErrInfo("任务已停用!");
                updateLog.setExeStatus("I");// 执行成功
                param.put("log", updateLog);
                exeLogMapper.updateTaskDispatchExeLog(param);

                // 处理依赖任务
                try {
                    TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(taskLog.getId());
                    TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper);
                    util.exeAfterTask(taskInfo, taskPlan);
                } catch (Exception e) {
                    log.error("执行依赖异常", e);
                }

                return;
            }
            ////////////////////////////////////////////////////////////////////

            if ("C".equals(taskInfo.getIsBefore())) {
                TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(logId);
                // 20220505 update by jack.zhang 检查文件是否存在
                if (StringUtils.isEmpty(taskPlan.getFileName()) || StringUtils.isEmpty(taskLog.getExtend1())) {
                    log.warn("[" + taskInfo.getTaskName() + "]文件未到位，不执行! fileInfo:{}-{}", taskPlan.getFileName(), taskLog.getExtend1());
                    TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                    updateLog.setId(logId);
                    updateLog.setErrInfo("文件未到位，待执行!");
                    param.put("log", updateLog);
                    exeLogMapper.updateTaskDispatchExeLog(param);
                    return;
                }
            }

            // 调用任务前，先将任务的执行结束时间更新为空，用于标示是否已经回调,更新任务执行状态为执行中，并记录任务开始执行时间
            synchronized (StartTaskThread.class) {
                TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(logId);
                //保证任务只会被调用一次
                if (!("P".equals(taskPlan.getExeStatus()) || "W".equals(taskPlan.getExeStatus()))) {
                    log.warn("[" + taskInfo.getTaskName() + "]任务状态[" + taskPlan.getExeStatus() + "]不是P或者W，不触发!");
                    return;
                } else {
                    log.info("更新任务" + taskId + "的执行计划[" + logId + "]状态为执行中");
                    TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                    updateLog.setId(logId);
                    updateLog.setExeEndTime("");
                    updateLog.setExeStartTime(TaskDateUtil.getMachingCurrentTime());
                    updateLog.setExeStatus("R");// 执行中
                    if (!"EndJob".equals(taskInfo.getTaskId())) {
                        updateLog.setErrInfo("任务执行中");
                    }
                    updateLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
                    param.put("log", updateLog);
                    exeLogMapper.updateTaskDispatchExeLog(param);
                }
            }

            // 处理传入参数
            Map<String, Object> paramsMap = new HashMap<>();
            // 20201106 update by jack.zhang 如果是条件依赖，把条件任务参数和文件编码放入参数中
            if ("C".equals(taskInfo.getIsBefore()) && StringUtil.isNotEmpty(taskInfo.getConditionParam())) {
                Map<String, Object> map = new HashMap<>();
                map.put("paramId", taskInfo.getConditionParam());
                TaskDispatchParamMapper paramMapper = io.github.openground.base.utils.SpringUtil.getBean("taskDispatchParamMapper");
                TaskDispatchParam p = paramMapper.selectByPrimaryKey(map);
                if (p != null) {
                    paramsMap.put("fileEncoding", p.getEncoding());
                    paramsMap.put("conditionParam", p);
                }
            }
            // 20201106 update by jack.zhang  end
            String params = taskInfo.getParams();
            if (!StringUtils.isEmpty(params)) {
                TaskDispatchServiceUtil.addParams(params, paramsMap);
            }
            paramsMap.put("taskPlanId", logId);
            if (fileName != null && taskLog.getExtend1() != null) {
                paramsMap.put("fileName", fileName);
                paramsMap.put("filePath", taskLog.getExtend1());// 文件下载后保存本地路径
            }
            paramsMap.put("taskConfig", taskInfo);
            if (!paramsMap.containsKey("sysEodDate")) {
                paramsMap.put("sysEodDate", TaskDateUtil.getSysEodDate(taskInfo.getCpsGroup()));
            }

            // 获取任务service
            log.info("获取[" + taskInfo.getTaskName() + "]任务,准备开始执行");
            String jobService = taskInfo.getTaskMethod();
            JobEngine job = SpringUtil.getBean(jobService);
            JobOut out = new JobOut();
            MDC.put("taskId", taskId);

            try {
                // 执行任务并获取返回结果
                out = job.execute(paramsMap);
            } catch (TransactionSystemException e) {
                String err = ExceptionUtil.stacktraceToString(e, 500);
                if (err != null && err.contains("connection closed")) {
                    log.warn("任务执行时间超过数据库链接强制回收时间，请注意");
                    out.setSuccess(true);
                    out.setMessage("执行成功，但执行时间超过数据库链接强制回收时间，请注意检查实际结果");
                } else {
                    throw e;
                }
            }

            MDC.remove("taskId");
            log.info("[" + taskInfo.getTaskName() + "]任务执行结束");

            // 20220824 update by jack.zhang 增加数据库资源未释放的处理
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                log.warn("当前任务事物未结束，调度引擎强制清理事物运行环境");
                TransactionSynchronizationManager.clear();  // 清理事务管理器
                SqlSessionTemplate st = SpringUtil.getBean("sqlSessionTemplate");
                Map<Object, Object> resourceMap = TransactionSynchronizationManager.getResourceMap();
                SqlSessionHolder holder = (SqlSessionHolder) resourceMap.get(st.getSqlSessionFactory());
                TransactionSynchronizationManager.unbindResourceIfPossible(st.getSqlSessionFactory()); // 解除当前线程SqlSessionHolder的绑定，防止出现从SqlSessionHolder中获取已关闭的链接
                if (holder != null) {
                    log.warn("SqlSessionHolder is not null, close current session");
                    holder.getSqlSession().commit();
                    SqlSessionUtils.closeSqlSession(holder.getSqlSession(), st.getSqlSessionFactory());
                    holder.reset();
                    /******************************************************************
                     * 这块还有问题：
                     * 虽然连接池监控显示链接已释放，活跃连接数	ActiveCount一直未增加
                     * 但是后续获取到链接时，会报Error committing transaction.  Cause: java.sql.SQLException: connection closed
                     * 解决方案：通过druid 的配置，对链接进行有效性检查：
                     * testOnBorrow: 设置从连接池获取连接时是否检查连接有效性，true检查，false不检查
                     * 但是这样做会牺牲性能，因此生产上不建议开启，测试时可以开启
                     * 最重要的还是可以提醒开发人员，当前任务有未提交的事物，解决代码问题吧
                     * 所以这个问题就不耗费精力和时间去研究了，交给能力强的人去处理吧 ^v^
                     * @Author:jack.zhang
                     * @Date 2022/8/25 15:17
                     ******************************************************************/
                    //  这块还有问题，当
                    // 获取当前线程的ConnectionHolder
                    Set set = resourceMap.keySet();
                    for (Object key : set) {
                        ConnectionHolder connectionHolder = (ConnectionHolder) resourceMap.get(key);
                        // 通过ConnectionHolder当前数据库连接
                        Connection conn = connectionHolder.getConnection();
                        conn.commit();
                        conn.close();
                        connectionHolder.reset();
                    }
                }
                out.setSuccess(false);
                out.setMessage(out.getMessage() + "!!!当前任务存在数据库事物未提交，请检查!!!");
            }
            // 20220824 update by jack.zhang end
            this.callback(taskInfo, out);
            // 处理依赖任务
            try {
                TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(taskLog.getId());
                TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper);
                util.exeAfterTask(taskInfo, taskPlan);
            } catch (Exception e) {
                log.error("执行依赖异常", e);
                MsgUtil.createErrMsg(taskInfo.getTaskName(), "执行后置依赖任务异常，请关注");
            }

        } catch (NoSuchBeanDefinitionException e) {
            log.error("任务配置错误:" + e.getMessage(), e);
            this.dealNoJobFind(taskInfo, e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (taskInfo != null) {
                this.dealException(taskInfo, e);
            }
        }

    }


    /**
     * 处理Bean找不到异常
     *
     * @param taskInfo
     * @param e
     * @author jack.zhang
     * @date 2017年3月16日 下午4:30:35
     * @version v_1.0
     */
    private void dealNoJobFind(TaskDispatchConfigDomain taskInfo, Exception e) {
        try {
            Map<String, Object> updateTaskMap = new HashMap<>();
            TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
            updateTask.setTaskId(taskId);
            updateTask.setStatus("0");
            updateTaskMap.put("task", updateTask);
            configMapper.updateTaskDispatchConfig(updateTaskMap);

            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            updateLog.setId(logId);
            updateLog.setExeStatus("E");
            updateLog.setExeEndTime("");
            updateLog.setErrInfo(e.getMessage());
            Map<String, Object> updateLogMap = new HashMap<>();
            updateLogMap.put("log", updateLog);
            exeLogMapper.updateTaskDispatchExeLog(updateLogMap);
            String msg = "主线程调度定时任务-" + taskId + "失败，请确认该任务的配置信息是否正确，同时在【查看执行日志】中查看错误信息。" + "该任务已经被置为失效，请修复后手动调用，并将状态改为启用。";
            log.error(msg);
        } catch (Exception e1) {
            log.error(e1.getMessage());
        }
    }

    /**
     * 异常处理
     *
     * @param taskInfo
     * @param e
     * @author jack.zhang
     * @date 2017年3月14日 下午3:11:27
     * @version v_1.0
     */
    private void dealException(TaskDispatchConfigDomain taskInfo, Exception e) {
        log.info("开始处理" + taskInfo.getTaskName() + "任务异常");
        TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
        updateTask.setTaskId(taskId);
        updateTask.setReExeTimes(null);
        updateTask.setMaxReExeTimes(null);

        // 自动重跑机制
        Integer reExeTimes = taskInfo.getReExeTimes() == null ? 0 : taskInfo.getReExeTimes();
        Integer maxReExeTimes = taskInfo.getMaxReExeTimes();

        Map<String, Object> updateTaskMap = new HashMap<>();
        Map<String, Object> updateLogMap = new HashMap<>();
        TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
        try {
            if (maxReExeTimes == -1) {// 无限重跑
                // 20210803 update by jack.zhang start
                if ("C".equals(taskInfo.getIsBefore())) {
                    updateLog.setExeStatus("W"); // 文件同步的状态改为W，重置为未执行，重新跑
                } else {
                    updateLog.setExeStatus("F"); // 定时执行的状态改为F，下次执行周期继续还可以跑
                }
                // 20210803 update by jack.zhang end
            } else if (reExeTimes < maxReExeTimes) { // 有重跑机会
                reExeTimes++;
                updateTask.setReExeTimes(reExeTimes);
                updateTaskMap.put("task", updateTask);
                configMapper.updateTaskDispatchConfig(updateTaskMap);
                // 20210803 update by jack.zhang start
                if ("C".equals(taskInfo.getIsBefore())) {
                    updateLog.setExeStatus("W"); // 文件同步的状态改为W，重置为未执行，重新跑
                } else {
                    updateLog.setExeStatus("P"); // 定时执行的状态改为P-待执行，下次主线程扫描到以后还可以跑
                }
                // 20210803 update by jack.zhang end
            } else {
                // 配置错误时，将该定时任务停用
                updateTask.setStatus("0");
                updateTaskMap.put("task", updateTask);
                configMapper.updateTaskDispatchConfig(updateTaskMap);

                // 20210803 update by jack.zhang 封装消息通知机制
                // 发送通知信息
                String msg = "主线程调度定时任务-" + taskId + "失败，请确认该任务的配置信息是否正确，同时在【查看执行日志】中查看错误信息。该任务已经被置为失效，请修复后手动调用，并将状态改为启用。";
                MsgUtil.createErrMsg(taskInfo.getTaskName(), msg);
                // 20210803 update by jack.zhang end
                updateLog.setExeStatus("E");
                log.error(msg);
            }
            // 将错误信息写入log表
            updateLog.setId(logId);
            updateLog.setExeEndTime("");
            String errorMsg = ExceptionUtil.stacktraceToString(e, 1000);
            updateLog.setErrInfo(errorMsg);
            updateLogMap.put("log", updateLog);
            exeLogMapper.updateTaskDispatchExeLog(updateLogMap);
            log.info(taskInfo.getTaskName() + "任务异常处理结束");
        } catch (Exception e1) {
            log.error("系统异常", e1);
        }
    }

    /**
     * 任务回掉机制
     *
     * @param taskInfo
     * @param out
     * @return
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月14日 下午3:09:49
     * @version v_1.0
     */
    private int callback(TaskDispatchConfigDomain taskInfo, JobOut out) throws Exception {
        log.info("任务计划[" + logId + "]执行结果：" + out.toString() + ",开始回调处理...");
        boolean success = out.getSuccess();
        String message = out.getMessage();
        String type = success == true ? "S" : "E";
        int count = 0;
        TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
        updateTask.setReExeTimes(null);
        updateTask.setMaxReExeTimes(null);
        updateTask.setTaskId(taskInfo.getTaskId());

        int reExeTimes = parseInt(taskInfo.getReExeTimes());

        if (success) {
            if (reExeTimes > 0) { // 将重跑次数恢复为0
                updateTask.setReExeTimes(0);
                log.info("任务日志表的：" + logId + "重跑成功，重跑次数已恢复为0。");
            }
        } else {
            // 20210803 update by jack.zhang 当任务中返回结果为false时，不进行异常处理，直接改为执行失败，状态为F，不影响下次重跑
            type = "F";
            // throw new Exception(message);
        }

        try {
            if (null != updateTask.getReExeTimes()) { // 更新重跑次数
                Map<String, Object> updateTaskMap = new HashMap<>();
                updateTaskMap.put("task", updateTask);

                configMapper.updateTaskDispatchConfig(updateTaskMap);
            }

            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            updateLog.setExeEndTime(TaskDateUtil.getMachingCurrentTime());
            // modify by ，异常信息截断处理，防止超长
            updateLog.setErrInfo(io.github.openground.base.utils.StringUtils.subByBytes(message,1000,"utf-8"));
            updateLog.setId(logId);
            updateLog.setExeStatus(type);
            Map<String, Object> updateLogMap = new HashMap<>();
            updateLogMap.put("log", updateLog);

            count = exeLogMapper.updateTaskDispatchExeLog(updateLogMap);
            log.info("任务计划[" + logId + "]回调处理完成");
        } catch (Exception e) {
            throw new Exception("任务回掉异常，异常信息：" + e);
        }
        return count;
    }

    public int parseInt(Integer times) {
        if (null == times) {
            return 0;
        }
        return times;
    }
}
