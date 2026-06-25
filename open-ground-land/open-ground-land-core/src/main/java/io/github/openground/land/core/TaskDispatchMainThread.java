package io.github.openground.land.core;

import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.util.StringUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.base.utils.MapUtil;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.util.MsgUtil;
import io.github.openground.land.common.util.PageData;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.config.TaskConfig;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 任务调度主线程
 *
 * @author jack.zhang
 * @version v_1.0
 * @date 2017年3月14日 下午3:59:11
 */
@Slf4j
public class TaskDispatchMainThread implements Runnable {

    // 主线程扫描周期，单位为秒
    private int scanningPeriod = 180;
    private TaskDispatchActiveHostMapper activeHostMapper;
    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private String cpsGroup;
    private TaskConfig taskConfig;

    private Map<String, Object> taskTimeMap = new HashMap<>();

    public TaskDispatchMainThread(TaskDispatchActiveHostMapper activeHostMapper,
                                  TaskDispatchConfigMapper configMapper,
                                  TaskDispatchExeLogMapper exeLogMapper,
                                  int scanningPeriod, String cpsGroup) {
        this.activeHostMapper = activeHostMapper;
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.scanningPeriod = scanningPeriod;
        this.cpsGroup = cpsGroup;
        this.taskConfig = TaskDispatchServiceUtil.getTaskConfig();
    }

    @Override
    public void run() {

        try {
            String currentTime = TaskDateUtil.getMachingCurrentTime();
            log.debug("作业调度主线程已启动，执行时间：" + currentTime);

            // 1.更新当前主机的活动时间
            this.updateActiveHost();

            if (!this.getActiveStatus()) {
                log.debug("当前主机引擎开关未开启，任务调度不执行");
                return;
            }

            // 2.根据主机活动状态，将不活动主机对应的任务重新分配给当前主机
            this.updateTaskExeHostIp();

            // 3.遍历当前主机待处理的定时任务，将执行计划写入log表, 当前任务执行异常后，是否仍然记录执行计划
            this.searchCurrentHostTask(currentTime);

            // 4.遍历未指定主机，或所指定的主机无法提供服务的定时任务
            this.searchOtherHostTask(currentTime);

            // 5.从日志表中取出待执行的任务，并将任务调起
            this.startTask();

        } catch (Throwable e) {
            log.error("调度引擎执行异常", e);
        }

    }

    /**
     * 更新当前主机的活动时间，利用时间戳来表明当前主机是否在活动
     *
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:07:09
     * @version v_1.0
     */
    private void updateActiveHost() throws Exception {
        log.debug("更新主机活动时间");
        String hostIp = TaskDispatchServiceUtil.getCpsHostIp();
        String currentTime = TaskDateUtil.getMachingCurrentTime();

        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        param.put("activeTime", currentTime);
        param.put("cpsGroup", cpsGroup);
        int count = activeHostMapper.updateActiveHost(param);
        if (0 == count) {
            param.put("sysEodDate", CommonUtil.getCurrDate(CommonUtil.yyyyMMdd));
            param.put("activeStatus", "OFF"); //默认关闭
            activeHostMapper.insertActiveHost(param);
        }
    }

    /**
     * 获取当前主机活动开关，若关闭则不执行定时任务
     *
     * @return
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:40:07
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    private boolean getActiveStatus() throws Exception {
        String hostStatus = "";
        String hostIp = TaskDispatchServiceUtil.getCpsHostIp();
        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        param.put("cpsGroup", cpsGroup);
        Map<String, Object> map = MapUtil.mapKeyUpperCase(activeHostMapper.selectActiveHostStatus(param));
        if (map != null && map.containsKey("ACTIVE_STATUS")) {
            hostStatus = (String) map.get("ACTIVE_STATUS");
            TaskDispatchServiceUtil.setHostStatus(hostStatus);
        }
        if ("ON".equals(hostStatus)) {
            return true;
        }
        return false;
    }

    /**
     * 如果代办任务对应的主机无法正常工作，则将代办任务的主机ip更改为当前主机ip
     *
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:07:25
     * @version v_1.0
     */
    private void updateTaskExeHostIp() throws Exception {
        log.debug("开始获取不活动的主机");

        String ipStr = "";
        List<String> cannotServiceHostIpList = getCannotServiceHostIp();
        for (String string : cannotServiceHostIpList) {
            ipStr += string + ";";
        }
        log.debug("获取到不活动主机：" + ipStr);
        if (cannotServiceHostIpList.size() > 0) { // 存在无法正常工作的主机
            Map<String, Object> param = new HashMap<>();
            param.put("list", cannotServiceHostIpList);
            param.put("activeHostIp", TaskDispatchServiceUtil.getCpsHostIp());
            param.put("extend5", "原主机无法正常工作，更换为新的主机:" + TaskDispatchServiceUtil.getCpsHostIp());
            log.debug("更新无法正常工作主机待执行的任务");
            activeHostMapper.updateHostIp(param);
        }
    }

    /**
     * 获得无法正常工作的主机IP 1.如果主机IP超过两个主线程扫描时间，未更新过活动状态，则认为当前主机无法正常工作
     * 2.定时任务制定的ip在TASK_DISPATCH_ACTIVE_HOST表中不存在的，则认为当前主机无法正常工作
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private List<String> getCannotServiceHostIp() throws Exception {

        String activeTime = TaskDateUtil.nextOrBeforPriodTime(TaskDateUtil.getMachingCurrentTime(), -this.scanningPeriod * 2, "s");
        Map<String, Object> param = new HashMap<>();
        param.put("activeTime", activeTime);
        param.put("cpsGroup", cpsGroup);
        return activeHostMapper.selectHostIpByActiveTime(param);
    }

    /**
     * 遍历当前主机待处理的定时任务，将执行计划写入log表
     *
     * @param currentTime
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:07:52
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    private void searchCurrentHostTask(String currentTime) throws Exception {
        synchronized (TaskDispatchMainThread.class) {
            log.info("开始生成当前主机满足执行条件的作业或任务执行计划");
            //加锁，防止重复初始化
            Map<String, Object> param = new HashMap<>();
            List<String> hostIpList = new ArrayList<>();
            hostIpList.add(TaskDispatchServiceUtil.getCpsHostIp());
            param.put("list", hostIpList);
            param.put("currentTime", currentTime);
            param.put("cpsGroup", cpsGroup); // 2021.11.8 update by jack.zhang 增加调度组条件，防止跨调度组生成任务计划
            List<TaskDispatchConfigDomain> taskDispatchConfigDomainList = configMapper.selectTaskByInfo(param);
            if (!taskDispatchConfigDomainList.isEmpty()) {
                for (TaskDispatchConfigDomain task : taskDispatchConfigDomainList) {
                    boolean b = this.checkExtTime(task);
                    if (!b) {
                        continue;
                    }
                    // 如果是作业，检查作业有没有没执行完的任务,不含可忽略的任务
                    if (task.getAfterTask() != null) {
                        PageData pd = new PageData();
                        pd.put("JOB_ID", task.getTaskId());
                        List<Map<String, Object>> list = exeLogMapper.findNotExeTaskPlan(pd);
                        if (list != null && !list.isEmpty()) {
                            Map<String, Object> w = list.get(0);
                            log.info("[" + task.getTaskId() + "]" + task.getTaskName() + "有未完成的任务:" + w.get("TASK_ID") + ",不生成执行计划!");
                            continue;
                        }
                    }
                    log.info("开始生成[" + task.getTaskId() + "]" + task.getTaskName() + "的执行计划");
                    // 生成批次号
                    int batchNo = TaskDispatchServiceUtil.getBatchNo(task.getJobId(), TaskDateUtil.getSysEodDate(task.getCpsGroup()), task.getCompany(), exeLogMapper);

                    String taskId = task.getTaskId();
                    TaskDispatchExeLogDomain taskLog = new TaskDispatchExeLogDomain();
                    taskLog.setId(KeyGenerator.getBusinessKey("TASK_LOG"));
                    taskLog.setEodDate(TaskDateUtil.getSysEodDate(task.getCpsGroup()));
                    taskLog.setTaskId(taskId);
                    taskLog.setJobId(taskId);//暂时先与taskid一致
                    taskLog.setPlanStartTime(task.getNextExeTime());
                    taskLog.setExeStartTime("");
                    taskLog.setExeEndTime("");
                    taskLog.setExeStatus("P"); // 状态为待处理
                    taskLog.setErrInfo("");
                    taskLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
                    taskLog.setMtTime(TaskDateUtil.getMachingCurrentTime());
                    taskLog.setBatchNo(batchNo + "");
                    taskLog.setExtend2("N");
                    taskLog.setCompany(task.getCompany());

                    exeLogMapper.insertTaskDispatchExeLog(taskLog);
                    log.info("成功生成[" + task.getTaskId() + "]" + task.getTaskName() + "的执行计划");
                    // 更新当前定时任务的下次执行时间
                    TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
                    updateTask.setReExeTimes(null);// 已重跑次数
                    updateTask.setMaxReExeTimes(null);// 最大重跑次数
                    updateTask.setTaskId(taskId);
                    String nextExeTime = task.getNextExeTime();
                    // 获取下次执行时间
                    nextExeTime = TaskDateUtil.nextOrBeforPriodTime(nextExeTime, Integer.parseInt(task.getExePeriodValue()), task.getExePeriodType());
                    updateTask.setNextExeTime(nextExeTime);
                    updateTask.setBatchNo(batchNo);
                    updateTask.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
                    Map<String, Object> updateTaskMap = new HashMap<>();
                    updateTaskMap.put("task", updateTask);
                    configMapper.updateTaskDispatchConfig(updateTaskMap);
                    if (task.getAfterTask() != null) {
                        TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper, taskLog.getPlanStartTime());
                        util.initJob(taskId, batchNo + "", null);
                    }
                }
            }
        }
    }

    /**
     * 遍历未指定主机，或所指定的主机无法提供服务的定时任务
     *
     * @param currentTime
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:08:04
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    private void searchOtherHostTask(String currentTime) throws Exception {
        log.info("开始生成未指定主机 满足执行条件的作业或任务执行计划");
        List<String> cannotServiceHostIpList = this.getCannotServiceHostIp();
        Map<String, Object> param = new HashMap<>();
        param.put("list", cannotServiceHostIpList);
        param.put("currentTime", currentTime);
        param.put("cpsGroup", cpsGroup);
        List<TaskDispatchConfigDomain> taskList = configMapper.selectTaskByIP(param);
        for (TaskDispatchConfigDomain task : taskList) {
            boolean exeTime = this.checkExtTime(task);
            // 如果是作业，检查作业有没有没执行完的任务,不含可忽略的任务
            if (task.getAfterTask() != null) {
                PageData pd = new PageData();
                pd.put("JOB_ID", task.getTaskId());
                List<Map<String, Object>> list = exeLogMapper.findNotExeTaskPlan(pd);
                if (list != null && !list.isEmpty()) {
                    Map<String, Object> w = list.get(0);
                    log.info("[" + task.getTaskId() + "]" + task.getTaskName() + "有未完成的任务:" + w.get("TASK_ID") + ",不生成执行计划!");
                    continue;
                }
            }
            log.info("开始生成[" + task.getTaskId() + "]" + task.getTaskName() + "的执行计划");
            // 生成批次号
            int batchNo = TaskDispatchServiceUtil.getBatchNo(task.getJobId(), TaskDateUtil.getSysEodDate(task.getCpsGroup()), task.getCompany(), exeLogMapper);

            String taskId = task.getTaskId();
            // 更新定时任务下次执行时间
            TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
            updateTask.setReExeTimes(null);
            updateTask.setMaxReExeTimes(null);
            updateTask.setTaskId(task.getTaskId());
            String planStartTime = task.getNextExeTime();
            String nextExeTime = TaskDateUtil.nextOrBeforPriodTime(planStartTime, Integer.parseInt(task.getExePeriodValue()), task.getExePeriodType());
            updateTask.setNextExeTime(nextExeTime);
            if (exeTime) { //若在时间内则更新批次号
                updateTask.setBatchNo(batchNo);
            }
            Map<String, Object> updateTaskMap = new HashMap<>();
            updateTaskMap.put("task", updateTask);
            updateTaskMap.put("nextExeTime", planStartTime);
            if (!exeTime) {
                configMapper.updateTaskDispatchConfig(updateTaskMap);
                log.info("[" + task.getTaskId() + "]" + task.getTaskName() + "不在执行时间段内，不执行!");
                continue;
            }
            updateTask.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
            int updateCount = configMapper.updateTaskDispatchConfig(updateTaskMap);
            if (updateCount > 0) { // 更新成功，则表示当前主机抢到调度权，将执行计划插入日志表
                TaskDispatchExeLogDomain taskLog = new TaskDispatchExeLogDomain();
                taskLog.setId(KeyGenerator.getBusinessKey("TASK_LOG"));
                taskLog.setEodDate(TaskDateUtil.getSysEodDate(task.getCpsGroup()));
                taskLog.setTaskId(taskId);
                taskLog.setJobId(taskId);//暂时先与taskid一致
                taskLog.setPlanStartTime(planStartTime);
                taskLog.setExeStartTime("");
                taskLog.setExeEndTime("");
                taskLog.setExeStatus("P"); // 状态为待处理
                taskLog.setErrInfo("");
                taskLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
                taskLog.setMtTime(TaskDateUtil.getMachingCurrentTime());
                taskLog.setBatchNo(batchNo + "");
                taskLog.setExtend2("N");
                taskLog.setCompany(task.getCompany());
                exeLogMapper.insertTaskDispatchExeLog(taskLog);
                log.info("成功生成[" + task.getTaskId() + "]" + task.getTaskName() + "的执行计划");
                if (task.getAfterTask() != null) {
                    TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper, planStartTime);
                    util.initJob(taskId, batchNo + "", null);
                }

            }
        }
    }


    /**
     * 从日志表中取出待执行的任务，并将任务调起 对于同一taskId 如果有任务任然为执行中的状态，则本次调度不处理该任务
     *
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月13日 下午5:08:14
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    private void startTask() throws Exception { // 调用方式为异步

        log.info("开始获取当前主机待执行任务执行计划");
        String exeCurHostIp = TaskDispatchServiceUtil.getCpsHostIp();//取出状态为P或R的执行计划
        Map<String, Object> param = new HashMap<>();
        param.put("exeCurHostIp", exeCurHostIp);
        param.put("cpsGroup", cpsGroup);
        List<TaskDispatchExeLogDomain> waitDispatchTaskList = exeLogMapper.selectWaitDispatchTaskByHostIp(param);
        // v4.1.0 update by jack.zhang 20210107 解决判断执行中的任务条件有误，导致任务执行中任务又被调起
        /**
         * 修改原来通过执行中taskId和当前taskId比较的方式，若有多个执行中的任务，会被覆盖
         * 改为若在执行中，则放入map中，若key值存在，则任务有任务在执行
         * */
        Map<String, String> exeingTaskMap = new HashMap<>();
        for (TaskDispatchExeLogDomain task : waitDispatchTaskList) {
            Thread.sleep(1000);
            String id = task.getId();
            String taskId = task.getTaskId();
            String status = task.getExeStatus();
            TaskDispatchConfigDomain taskInfo = configMapper.selectTaskDispatchConfigByPK(taskId);
            if ("R".equals(status)) { // 执行中的不调起
                exeingTaskMap.put(taskId, taskId);
                // update by jack.zhang 20211028 start
                Map<String, Object> taskCenter = taskConfig.getTaskCenter();
                boolean alarmEnabled = taskCenter.containsKey("alarmEnabled") ? (boolean) taskCenter.get("alarmEnabled") : false;
                if (alarmEnabled) {
                    // 检查任务执行时间，如果超过两个执行周期，则进行预警提示
                    TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(task.getId());
                    int periodValue = taskCenter.containsKey("alarmFileExeMaxTime") ? (int) taskCenter.get("alarmFileExeMaxTime") : 1;
                    int alarmPeriodSize = taskCenter.containsKey("alarmPeriodSize") ? (int) taskCenter.get("alarmPeriodSize") : 2;
                    String exePeriodType = "h";
                    if ("N".equals(taskInfo.getIsBefore())) {
                        periodValue = Integer.parseInt(taskInfo.getExePeriodValue());
                        exePeriodType = taskInfo.getExePeriodType();
                    }
                    String nextPriodTime = TaskDateUtil.nextOrBeforPriodTime(taskPlan.getExeStartTime(), alarmPeriodSize * periodValue, exePeriodType);
                    // 判断执行时间是否超过设置的周期
                    String currentTime = TaskDateUtil.getMachingCurrentTime();
                    if (currentTime.compareTo(nextPriodTime) > 0) { // 结果大于0，说明当前时间比设定的周期值大
                        // 判断是否超过通知周期，不然每次主线程活动都会通知，30秒一次太频繁了
                        String lastNoticeTime = CommonUtil.getStringValueFromHashMap(taskTimeMap, taskId);
                        // 获取上次通知时间，如果为空或者当前时间 大于 上次通知时间+10分钟，则进行通知
                        if (StringUtil.isEmpty(lastNoticeTime) || currentTime.compareTo(TaskDateUtil.nextOrBeforPriodTime(lastNoticeTime, 10, "m")) > 0) {
                            log.warn("任务执行时间超过{}个周期，目前还在执行中，生成告警通知", alarmPeriodSize);
                            MsgUtil.createErrMsg(taskInfo.getTaskName(), "任务执行时间超过2个周期(" + alarmPeriodSize * periodValue + exePeriodType + ")，目前还在执行中，请查看日志并检查任务是否正常");
                            taskTimeMap.put(taskId, currentTime);
                        }
                    } else {
                        taskTimeMap.remove(taskId);
                    }
                }
                // update by jack.zhang 20211028 endt
                continue;
            }
            if (exeingTaskMap.containsKey(taskId)) { // 若该任务有执行中的，则将下次执行计划分配给其他可提供服务的主机
                // v4.1.0 update by jack.zhang 20210107 若任务指定主机运行，则只在指定主机执行，只有当所指定主机不活动时，才将任务分配给其他主机
                if (taskInfo != null && ObjectUtil.isNull(taskInfo.getExeHostIp())) {
                    List<String> serviceHostIp = this.getServiceHostIpByTask(taskId, exeCurHostIp);
                    if (serviceHostIp != null && !serviceHostIp.isEmpty()) {
                        TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                        Map<String, Object> updateLogMap = new HashMap<>();
                        updateLog.setId(id);
                        updateLog.setExeCurHostIp(serviceHostIp.get(0));
                        updateLogMap.put("log", updateLog);
                        exeLogMapper.updateTaskDispatchExeLog(updateLogMap);
                    }
                }
            } else {
                log.info("准备调起任务[" + task.getTaskId() + "]的执行计划:" + task.getId());
                ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
                executor.execute(new StartTaskThread(configMapper, exeLogMapper, task));
            }
        }
    }

    /**
     * 根据taskId获取可以提供服务的主机，排除掉有该任务正在执行或待执行的
     *
     * @param exeingTaskId
     * @param exeingHostIp
     * @return
     * @throws Exception
     * @author jack.zhang
     * @date 2017年3月22日 上午10:33:02
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    private List<String> getServiceHostIpByTask(String exeingTaskId, String exeingHostIp) throws Exception {
        Map<String, Object> param = new HashMap<>();
        param.put("exeingTaskId", exeingTaskId);
        param.put("exeingHostIp", exeingHostIp);
        String activeTime = TaskDateUtil.nextOrBeforPriodTime(TaskDateUtil.getMachingCurrentTime(), -this.scanningPeriod * 2, "s");
        param.put("activeTime", activeTime);
        param.put("cpsGroup", cpsGroup);
        return activeHostMapper.selectServiceHostIpByTask(param);
    }


    /**
     * 判断当前时间是否在任务执行时间段内
     *
     * @param task
     * @return
     * @throws ParseException
     * @author jack.zhang
     * @date 2017年4月17日 下午4:30:00
     * @version v_1.0
     */
    private boolean checkExtTime(TaskDispatchConfigDomain task) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        Date now = sdf.parse(TaskDateUtil.getCurrTime());
        Date start = sdf.parse(task.getStartTime());
        Date end = sdf.parse(task.getEndTime());
        Map<String, Object> paramsMap = new HashMap<>();
        if (now.after(start) && now.before(end)) {
            String params = task.getParams();
            if (!StringUtils.isEmpty(params)) {
                TaskDispatchServiceUtil.addParams(params, paramsMap);
                if (paramsMap.containsKey("stopDate")) {
                    // 判断跑批日期是否在停止日之后
                    String stopDate = CommonUtil.getStringValueFromHashMap(paramsMap, "stopDate").replaceAll("-", "");
                    String sysEodDate = TaskDateUtil.getSysEodDate(task.getCpsGroup());
                    if (sysEodDate.compareTo(stopDate) > 0) {
                        log.info("当前跑批日期已超过停止日期，不执行任务!停止日期：" + stopDate + ",当前跑批日期：" + sysEodDate + "。任务ID：" + task.getTaskId() + "，任务名称：" + task.getTaskName() + "。");
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

}
