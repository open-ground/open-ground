package io.github.openground.land.core;

import com.github.pagehelper.util.StringUtil;
import io.github.openground.base.utils.CommonUtil;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.entity.TaskDispatchParam;
import io.github.openground.land.common.util.PageData;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.config.TaskConfig;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.mapper.TaskDispatchParamMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 任务调度工具类
 *
 * @author jack.zhang
 * @version v 1.0
 * @date 2019年2月21日 下午10:08:48
 */
@Slf4j
public class TaskDispatchServiceUtil {

    private static String hostStatus;

    private static String cpsGroup;

    private static String cpsHostIp;

    private static String cpsGroupMdc;

    private static TaskConfig taskConfig;

    /**
     * <p>Description: 获取mdclog文件名称</P>
     * 调度组名+dispatch，例如：liquidity-batch-task-fps-dispatch
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/24 8:52
     * @param
     * @return java.lang.String
    */
    public static String getCpsGroupMdc() {
        if (cpsGroupMdc == null) {
            cpsGroupMdc = getCpsGroup().toLowerCase() + "-dispatch";
        }
        return cpsGroupMdc;
    }

    public static TaskConfig getTaskConfig() {
        return taskConfig;
    }

    public static void setTaskConfig(TaskConfig taskConfig) {
        TaskDispatchServiceUtil.taskConfig = taskConfig;
    }

    public static String getCpsHostIp() {
        return cpsHostIp;
    }

    public static void setCpsHostIp(String cpsHostIp) {
        TaskDispatchServiceUtil.cpsHostIp = cpsHostIp;
    }

    public static String getCpsGroup() {
        return cpsGroup;
    }

    public static void setCpsGroup(String cpsGroup) {
        TaskDispatchServiceUtil.cpsGroup = cpsGroup;
    }

    public static String getHostStatus() {
        return hostStatus;
    }

    public static void setHostStatus(String status) {
        hostStatus = status;
    }

    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private TaskDispatchParamMapper paramMapper;


    /**
     * 任务计划执行时间
     */
    private String planStartTime;

    public TaskDispatchServiceUtil() {

    }

    private static ExecutorService poolExecutor;

    public static  ExecutorService getPoolExecutor() {
        return poolExecutor;
    }


    public static void setPoolExecutor(ExecutorService poolExecutor) {
        TaskDispatchServiceUtil.poolExecutor = poolExecutor;
    }

    public TaskDispatchServiceUtil(TaskDispatchConfigMapper configMapper, TaskDispatchExeLogMapper exeLogMapper) {
        this.configMapper = configMapper; this.exeLogMapper = exeLogMapper;
    }

    /**
     * @param configMapper 配置Mapper
     * @param exeLogMapper 执行日志Mapper
     * @param planStartTime 任务计划执行时间
     */
    public TaskDispatchServiceUtil(TaskDispatchConfigMapper configMapper, TaskDispatchExeLogMapper exeLogMapper, String planStartTime) {
        this.configMapper = configMapper; this.exeLogMapper = exeLogMapper;
        this.planStartTime = planStartTime;
    }

    /**
     * 更新任务执行计划,不追加历史未执行计划
     *
     * @param configMapper 配置Mapper
     * @param taskId 任务ID
     * @param cpsGroup CPS组
     * @throws Exception 异常
     * @author jack.zhang
     * @date 2017年3月14日 下午6:03:32
     * @version v_1.0
     */
    @SuppressWarnings("unchecked")
    public void updateTaskDispatchConfig(TaskDispatchConfigMapper configMapper, String taskId, String cpsGroup) throws Exception {
        log.info("开始更新任务执行计划");
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("taskId", taskId);
        param.put("cpsGroup", cpsGroup);
        List<TaskDispatchConfigDomain> list = configMapper.selectValidInfo(param);
        for (TaskDispatchConfigDomain task : list) {
            TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
            String nextExeTime = task.getNextExeTime();
            nextExeTime = TaskDateUtil.nextPriodTimeNoAddition(nextExeTime, task.getExePeriodType());
            Map<String, Object> updateTaskMap = new HashMap<String, Object>();
            updateTask.setTaskId(task.getTaskId());
            updateTask.setNextExeTime(nextExeTime);
            updateTaskMap.put("task", updateTask);
            configMapper.updateTaskDispatchConfig(updateTaskMap);
        }
        log.info("任务执行计划更新成功");
    }

    /**
     * 获取批次号，每天从1开始
     *
     * @param task
     * @return
     * @description:
     * @author open-ground
     * @date 2018年9月11日 上午9:46:10
     */
    public static int getBatchNo(TaskDispatchConfigDomain task) {
        int bacthNo;
        String updateTime = task.getUpdateTime();
        if (updateTime == null || task.getBatchNo() == null) {
            return 1;
        } else {
            String updateDate = updateTime.substring(0, 10);
            if (updateDate.equals(CommonUtil.getCurrDate(CommonUtil.yyyy_MM_dd))) {
                bacthNo = task.getBatchNo() + 1;
            } else {
                bacthNo = 1;
            }
        }
        return bacthNo;
    }

    public static int getBatchNo(String JobId, String eodDate, String company, TaskDispatchExeLogMapper exeLogMapper) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("jobId", JobId);
        queryParam.put("eodDate", eodDate);
        queryParam.put("company", company);
        String batchNoStr = exeLogMapper.getBatchNo(queryParam);
        if (batchNoStr == null || batchNoStr.isEmpty()) {
            return 1;
        }
        return Integer.parseInt(batchNoStr) + 1;
    }

    /**
     * 初始化作业信息，生成作业下所有任务的执行计划
     *
     * @param jobId   作业编码
     * @param batchNo 批次号
     * @throws Exception
     * @author jack.zhang
     * @data 2019年2月21日 上午8:22:14
     */
    public Map<String, Object> initJob(String jobId, String batchNo, String sysEodDate) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        if (jobId == null || "".equals(jobId)) {
            map.put("success", false);
            map.put("message", "作业编码不能为空");
            return map;
        }

        log.info("开始生成作业[" + jobId + "]的任务批次执行计划");
        if(sysEodDate == null){
            sysEodDate = TaskDateUtil.getSysEodDate(cpsGroup);
        }
        this.getAfterTask(jobId, batchNo, sysEodDate);
        log.info("作业[" + jobId + "]的任务批次执行计划生成结束");
        map.put("success", true);
        map.put("message", "作业初始化成功");
        return map;
    }

    /**
     * <p>Description: 生成后置任务的执行计划</P>
     *
     * @param jobId 作业编码
     * @param batchNo 批次号
     * @param sysEodDate 跑批日期
     * @return void
     * @Author:jack.zhang
     * @Date 2025/2/23 20:35
     */
    private void getAfterTask(String jobId, String batchNo, String sysEodDate) throws Exception {
       List<TaskDispatchConfigDomain> jobs = configMapper.selectTaskDispatchConfigByJobId(jobId);
        for (TaskDispatchConfigDomain task : jobs) {
            if(StringUtil.isEmpty(task.getBeforeTask()) && StringUtil.isEmpty(task.getAfterTask()) || task.getTaskId().equalsIgnoreCase(task.getJobId())){
                // 没有配置依赖的任务直接跳过, 开始作业已经生成计划了，也直接跳过
                continue;
            }
            saveTaskPlan(jobId, task.getTaskId(), batchNo, sysEodDate);
        }
    }
    /**
     * 获取后置任务
     *
     * @param taskId  任务编号
     * @param batchNo 批次号
     * @param jobId   作业编码
     * @throws Exception
     * @author jack.zhang
     * @data 2019年2月21日 上午8:23:40
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    private void getAfterTask(String taskId, String batchNo, String jobId, String sysEodDate) throws Exception {
        TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(taskId);
        log.info("任务[" + task.getTaskId() + "]-" + task.getTaskName() + "的后置任务为：" + task.getAfterTask());
        if (task != null && task.getAfterTask() != null) {
            String afterTaskId = task.getAfterTask();
            String[] afterTasks = afterTaskId.split(",");
            for (String id : afterTasks) {
                if (id == null || id.trim().isEmpty()) {
                    continue;
                }
                log.info("开始处理:" + id);
                TaskDispatchConfigDomain afterTask = configMapper.selectTaskDispatchConfigByPK(id);
                if (afterTask == null) {
                    log.warn("[" + task.getTaskName() + "]的后置任务:[" +  id + "]不存在，请检查任务依赖是否配置正确!");
                }
                ////////////////////////////////////////////////////////////////////
                //midify by ，停用的任务也生成执行计划，但是在执行时直接返回成功
                if (afterTask != null) {
                    // 判断后置任务中的前置任务是否都已经生成了计划
                    String beforeTaskStr = afterTask.getBeforeTask();
                    String[] beforeTasks = beforeTaskStr.split(",");
                    Map param = new HashMap();
                    param.put("EOD_DATE", sysEodDate);
                    param.put("BATCH_NO", batchNo);
                    param.put("JOB_ID", jobId);
                    param.put("tasks", beforeTasks);
                    List<String> status = new ArrayList<>();
                    status.add("W");
                    status.add("P");
                    status.add("I"); // 20210803 update by jack.zhang 增加已忽略的任务,防止任务依赖不完整
                    param.put("status", status);
                    // 根据作业编码、跑批日期、批次号和任务编码 查询生成计划成功待执行的任务数，和前置任务数做比较，若相同，则初始化生成该任务的计划
                    List<TaskDispatchExeLogDomain> beforeListSuc = exeLogMapper.findExeLog4checkBefore(param);
                    HashSet<String> h = new HashSet<String>();
                    for (TaskDispatchExeLogDomain t : beforeListSuc) {
                        h.add(t.getTaskId());
                    }
                    log.info("任务[" + afterTask.getTaskId() + "]有" + beforeTasks.length + "个前置任务，已经初始化了" + beforeListSuc.size() + "个执行计划");
                    if (beforeTasks.length == h.size()) {
                        log.info("任务[" + afterTask.getTaskId() + "]满足初始化条件");
                        saveTaskPlan(jobId, afterTask.getTaskId(), batchNo, sysEodDate);
                        log.info("生成任务[" + afterTask.getTaskId() + "]-" + afterTask.getTaskName() + "的执行计划成功");
                        this.getAfterTask(afterTask.getTaskId(), batchNo, jobId, sysEodDate);
                    } else {
                        log.info("任务[" + afterTask.getTaskId() + "]不满足初始化条件");
                    }

                }
            }
        }
    }

    /**
     * 保存任务执行计划
     *
     * @param jobId   作业编码
     * @param taskId  任务编码
     * @param batchNo
     * @throws Exception
     * @author jack.zhang
     * @data 2019年2月20日 下午7:09:05
     */
    public TaskDispatchExeLogDomain saveTaskPlan(String jobId, String taskId, String batchNo, String sysEodDate) throws Exception {
        String isIgnore = "N";
        TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(taskId);
        String paramId = task.getConditionParam();
        if (StringUtil.isNotEmpty(paramId)) {
            TaskDispatchParam q = new TaskDispatchParam();
            q.setParamId(paramId);
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("paramId", q.getParamId());
            TaskDispatchParam param = paramMapper.selectOne(paramMap);
            if (param != null && "Y".equals(param.getIsIgnore())) {
                isIgnore = param.getIsIgnore();
            }
        } else {
            isIgnore = "N";
        }
        TaskDispatchExeLogDomain taskLog = new TaskDispatchExeLogDomain();
        taskLog.setId(KeyGenerator.getBusinessKey("TASK_LOG"));
        taskLog.setJobId(jobId);
        taskLog.setEodDate(sysEodDate);
        taskLog.setTaskId(taskId);
        taskLog.setPlanStartTime(this.planStartTime);
        taskLog.setExeStartTime("");
        taskLog.setExeEndTime("");
        taskLog.setExeStatus("W"); // 状态为未执行
        taskLog.setErrInfo("");
        taskLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
        taskLog.setMtTime(TaskDateUtil.getMachingCurrentTime());
        taskLog.setBatchNo(batchNo);
        taskLog.setExtend2(isIgnore);
        taskLog.setExtend3(task.getTaskName());
        taskLog.setCompany(task.getCompany());
        exeLogMapper.insertTaskDispatchExeLog(taskLog);
        return taskLog;
    }

    /***
     * 执行后置任务
     * @param taskInfo
     * @param taskLog
     * @throws Exception
     * @author jack.zhang
     * @data 2019年2月21日 下午1:52:20
     */
    @SuppressWarnings("unchecked")
    public void exeAfterTask(TaskDispatchConfigDomain taskInfo, TaskDispatchExeLogDomain taskLog) throws Exception {

        String afterTask = taskInfo.getAfterTask();
        if (afterTask != null && !"".equals(afterTask)) {
            String[] afterTasks = afterTask.split(",");
            log.info("开始处理任务[" + taskInfo.getTaskName() + "]的后置任务:" + afterTask);
            // 检查每个后置任务
            for (String taskId : afterTasks) {
                TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(taskId);
                // 在任务调度执行链路中，调度器执行时可先校验任务配置是否有效，对已经生成历史执行计划的任务，执行前验证任务是否存在，当检测到任务已被删除时，系统自动将该执行计划标记为忽略状态，被忽略的任务不会中断整体执行流程，后续任务可正常执行
                if (task == null) {
                    // 修改历史执行计划表当前批次中已经生成历史执行计划的任务状态为 忽略
                    Map<String, Object> updateParamMap = new HashMap<>();
                    updateParamMap.put("company", taskLog.getCompany());
                    updateParamMap.put("jobId", taskLog.getJobId());
                    updateParamMap.put("eodDate", taskLog.getEodDate());
                    updateParamMap.put("batchNo", taskLog.getBatchNo());
                    updateParamMap.put("exeStatus", "I");
                    updateParamMap.put("errInfo", "任务已删除，自动忽略！");
                    exeLogMapper.updateStatus(updateParamMap);
                    log.warn("[" + taskInfo.getTaskName() + "]的后置任务:" +  afterTask + "不存在，忽略后置任务执行，请检查任务依赖是否配置正确!");
                    continue;
                }
                String beforeTask = task.getBeforeTask();
                String[] tasks = beforeTask.split(",");
                // 判断该任务的所有前置任务是否执行结束
                PageData param = new PageData();
                param.put("EOD_DATE", taskLog.getEodDate());
                param.put("BATCH_NO", taskLog.getBatchNo());
                param.put("JOB_ID", taskLog.getJobId());
                param.put("tasks", tasks);
                List<String> status = new ArrayList<String>();
                status.add("S");
                status.add("I");
                param.put("status", status);
                // 根据作业编码、跑批日期、批次号和任务编码 查询执行成功的任务数，和前置任务数做比较，若相同，则任务前置任务都执行成功
                // 20190308 增加查询出参数表TASK_DISPATCH_PARAM 配置可忽略的任务，即当前置任务为可忽略任务，即使条件不满足时也可以执行
                List<TaskDispatchExeLogDomain> beforeList = exeLogMapper.findExeLog4checkBefore(param);
                List<TaskDispatchExeLogDomain> ignoreList = exeLogMapper.findExeLog4checkBeforeIgnore(param);
                List<TaskDispatchConfigDomain> beforeListStatus0 = configMapper.findTaskByStatus0(param);
                HashSet<String> h = new HashSet<String>();
                for (TaskDispatchExeLogDomain t : beforeList) {
                    h.add(t.getTaskId());
                }
                for (TaskDispatchExeLogDomain t : ignoreList) {
                    h.add(t.getTaskId());
                }
                for (TaskDispatchConfigDomain t : beforeListStatus0) {
                    h.add(t.getTaskId());
                }
                log.info("任务[" + task.getTaskId() + "]有" + tasks.length + "个前置任务，已经执行成功" + beforeList.size() + "个,可忽略执行的任务" + (ignoreList.size() + beforeListStatus0.size()) + "个");
                if (tasks.length == h.size()) {
                    log.info("任务[" + taskId + "]满足执行条件，准备开始掉起");
                    // 获取任务执行计划
                    PageData pd = new PageData();
                    pd.put("TASK_ID", taskId);
                    pd.put("EOD_DATE", taskLog.getEodDate());
                    pd.put("BATCH_NO", taskLog.getBatchNo());
                    pd.put("JOB_ID", taskLog.getJobId());
                    // 若任务无效，不会生成对应的执行计划，所以需要判断
                    TaskDispatchExeLogDomain afterExeJob = exeLogMapper.findExeLog4ExeAfter(pd);
                    if (afterExeJob != null) {
                        // StartTaskThread startTaskThread = new StartTaskThread(dao, afterExeJob);
                        // startTaskThread.start();
                        ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
                        executor.execute(new StartTaskThread(configMapper, exeLogMapper, afterExeJob));
                    }
                } else {
                    log.info("任务[" + task.getTaskId() +  "]不满足执行条件");
                }
            }
        }
    }

    /**
     * 处理传入参数-string参数转换为map
     *
     * @param params
     * @param paramsMap
     * @throws Exception
     * @author jack.zhang
     * @data 2019年3月20日 上午9:00:54
     */
    public static void addParams(String params, Map<String, Object> paramsMap) throws Exception {

        if (params.endsWith(";")) { // 去掉最后一个；
            params = params.substring(0, params.length() - 1);
        }

        String[] paramArr = params.split(";");
        for (String param : paramArr) {
//			String[] p = param.split(":");
//
//			String key = p[0];
//			String value = p[1];
            // 20191218 jack.zhang 把通过:拆分获取key和value的方式改为通过第一个:截取获得，避免参数中有多个:的情况下获取值不对
            String key = param.substring(0, param.indexOf(":"));
            String value = param.substring(param.indexOf(":") + 1, param.length());
            // update by jack.zhang end
            if (value.startsWith("${") && value.endsWith("}")) { // 动态指定的参数
                value = value.substring(2, value.length() - 1);

                if ("machineDate".equals(value)) {
                    paramsMap.put(key, TaskDateUtil.getMachingCurrentDate());
                } else if ("sysEodDate".equals(value)) {
                    if (!paramsMap.containsKey("sysEodDate")) {
                        paramsMap.put(key, TaskDateUtil.getSysEodDate(cpsGroup));
                    }
                } else {
                    throw new Exception("无法解析：" + key + "对应的值，请联系管理员。");
                }
            } else {
                paramsMap.put(key, value);
            }
        }
    }

}
