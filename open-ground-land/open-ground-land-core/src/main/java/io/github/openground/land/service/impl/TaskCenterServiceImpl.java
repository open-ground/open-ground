package io.github.openground.land.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.utils.SpringUtil;
import io.github.openground.land.api.dto.TaskCenterRequest;
import io.github.openground.land.api.dto.TaskDispatchParamRequest;
import io.github.openground.land.api.dto.TaskMonitorRequest;
import io.github.openground.land.api.dto.TaskMonitorResponse;
import io.github.openground.land.api.dto.TaskDashboardResponse;
import io.github.openground.land.api.dto.TaskSegmentRequest;
import io.github.openground.land.common.constants.ErrorCode;
import io.github.openground.land.api.domain.StepSegmentExeInfo;
import io.github.openground.land.common.entity.ScheduleDomain;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogExt;
import io.github.openground.land.common.entity.TaskDispatchParam;
import io.github.openground.land.common.entity.TaskDispatchStepLog;
import io.github.openground.land.common.util.PWDDes;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.core.ConditionJobThread;
import io.github.openground.land.core.ExecuteJobThread;
import io.github.openground.land.core.StartTaskThread;
import io.github.openground.land.core.TaskCenterStartThread;
import io.github.openground.land.core.TaskDispatchServiceUtil;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.mapper.TaskDispatchParamMapper;
import io.github.openground.land.mapper.TaskDispatchStepLogMapper;
import io.github.openground.land.service.TaskCenterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * 任务中心服务实现
 * <p>从 land-develop 迁移：TaskCenterComponent → TaskCenterServiceImpl</p>
 *
 * @author jack.zhang
 * @since 2026-06-25
 */
@Slf4j
@Service
public class TaskCenterServiceImpl implements TaskCenterService {

    @Autowired
    private TaskDispatchConfigMapper configMapper;

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    @Autowired
    private TaskDispatchActiveHostMapper activeHostMapper;

    @Autowired
    private TaskDispatchParamMapper paramMapper;

    @Autowired
    private TaskDispatchStepLogMapper stepLogMapper;

    private boolean hasEndTask = false;
    private String endJobId = "";

    @Value("${spring.application.name}")
    private String cpsGroup;

    Map<String, TaskDispatchConfigDomain> allTaskMap = new HashMap<>();

    // ==================== 任务列表查询 ====================

    @Override
    public CommonResult<?> query(TaskCenterRequest request) {
        TaskDispatchConfigDomain queryParam = new TaskDispatchConfigDomain();
        BeanUtil.copyProperties(request, queryParam);
        if (request.getSysHead() != null) {
            queryParam.setCompany(request.getSysHead().getCompany());
        }
        if (ObjectUtil.isNull(request.getJobId())) {
            queryParam.setIsBefore("N");
            queryParam.setQueryOrder("NEXT_EXE_TIME, TASK_ID");
        } else {
            queryParam.setQueryOrder("MT_TIME ASC");
        }
        log.debug("============> 分页查询");
        List<TaskDispatchConfigDomain> resultList = new ArrayList<>();
        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<TaskDispatchConfigDomain> pageList = configMapper.datalistPage(queryParam);
        PageInfo<TaskDispatchConfigDomain> page = new PageInfo<>(pageList);

        TaskDispatchConfigDomain allQueryParam = new TaskDispatchConfigDomain();
        if (request.getSysHead() != null) {
            allQueryParam.setCompany(request.getSysHead().getCompany());
        }
        List<TaskDispatchConfigDomain> allList = configMapper.datalistPage(allQueryParam);
        for (TaskDispatchConfigDomain config : allList) {
            allTaskMap.put(config.getTaskId(), config);
        }
        for (TaskDispatchConfigDomain task : page.getList()) {
            boolean y = allList.stream().anyMatch(t ->
                    ObjectUtil.isNotNull(t.getJobId()) && t.getJobId().equalsIgnoreCase(task.getTaskId()) && !t.getJobId().equalsIgnoreCase(t.getTaskId())
            );
            task.setIsAfter(y ? "Y" : "N");
            resultList.add(task);
        }
        String hostIp = TaskDispatchServiceUtil.getCpsHostIp();
        Map<String, Object> param = new HashMap<>();
        param.put("hostIp", hostIp);
        if (StrUtil.isBlank(request.getCpsGroup())) {
            param.put("cpsGroup", cpsGroup);
        } else {
            param.put("cpsGroup", request.getCpsGroup());
        }
        List<Map<String, Object>> hostList = activeHostMapper.hostListlistPage(param);
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", resultList);
        result.put("totalrecord", page.getTotal());
        if (hostList != null && !hostList.isEmpty()) {
            Map<String, Object> pd = hostList.get(0);
            if (pd.containsKey("ACTIVE_TIME")) {
                result.put("activeTime", pd.get("ACTIVE_TIME"));
                result.put("sysEodDate", pd.get("SYS_EOD_DATE"));
            } else {
                result.put("activeTime", pd.get("active_time"));
                result.put("sysEodDate", pd.get("sys_eod_date"));
            }
        }
        allTaskMap.clear();
        return CommonResult.success(result);
    }

    // ==================== 查询主机列表 ====================

    @Override
    public CommonResult<?> queryHostList(TaskCenterRequest request) {
        List<Map<String, Object>> list = activeHostMapper.hostListlistPage(null);
        List<Map<String, Object>> result = new ArrayList<>();
        // 兼容 PgSql 模式 防止 key 为小写时，前台无法获取
        list.forEach(item -> {
            Map<String, Object> p = new HashMap<>();
            for (Object key : item.keySet()) {
                p.put(key.toString().toUpperCase(), item.get(key));
            }
            result.add(p);
        });
        return CommonResult.success(result);
    }

    // ==================== 新增任务 ====================

    @Override
    public CommonResult<?> add(TaskCenterRequest request) {
        TaskDispatchConfigDomain po = new TaskDispatchConfigDomain();
        BeanUtil.copyProperties(request, po);
        if (request.getSysHead() != null) {
            po.setCompany(request.getSysHead().getCompany());
            po.setMtUser(request.getSysHead().getUserId());
        }
        po.setMtTime(TaskDateUtil.getMachingCurrentTime());
        po.setBatchNo(1);
        po.setStatus("0"); // 新增时默认停用
        po.setNextExeTime(po.getFirstExeTime());
        try {
            try {
                configMapper.insert(po);
            } catch (Exception e) {
                log.warn("新增任务异常", e);
                po.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
                configMapper.updateById(po);
            }
        } catch (Exception e) {
            log.error("新增任务异常", e);
            throw new RuntimeException("999999", e);
        }
        return CommonResult.success(null);
    }

    // ==================== 修改任务 ====================

    @Override
    public CommonResult<?> update(TaskCenterRequest request) {
        TaskDispatchConfigDomain po = new TaskDispatchConfigDomain();
        BeanUtil.copyProperties(request, po);
        TaskDispatchConfigDomain config = configMapper.selectById(po.getTaskId());
        if (config == null) {
            throw new RuntimeException("990001");
        }
        po.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
        configMapper.updateTaskDispatchConfig(buildTaskMap(po));
        if ("isStartOrStop".equals(request.getIsStartOrStop()) && "1".equals(po.getStatus())) {
            TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper);
            String taskId = po.getTaskId();
            try {
                util.updateTaskDispatchConfig(configMapper, taskId, null);
            } catch (Exception e) {
                log.error("更新任务执行计划异常", e);
            }
        }
        try {
            setAfterTask(po);
            setOldAfterTask(request);
        } catch (Exception e) {
            log.error("修改任务异常", e);
            throw new RuntimeException("999999", e);
        }
        return CommonResult.success(null);
    }

    /**
     * 构建 updateTaskDispatchConfig 所需的 map 结构（包含 task 嵌套 key）
     */
    private Map<String, Object> buildTaskMap(TaskDispatchConfigDomain po) {
        Map<String, Object> taskMap = new HashMap<>();
        taskMap.put("task", po);
        taskMap.put("taskId", po.getTaskId());
        return taskMap;
    }

    /**
     * 设置后置任务
     */
    private void setAfterTask(TaskDispatchConfigDomain task) {
        String beforeTask = task.getBeforeTask();
        if (beforeTask != null && !beforeTask.trim().isEmpty()) {
            String[] beforeTasks = beforeTask.split(",");
            for (String taskId : beforeTasks) {
                TaskDispatchConfigDomain beftask = configMapper.selectTaskDispatchConfigByPK(taskId);
                if (beftask != null) {
                    String after = beftask.getAfterTask();
                    if (StrUtil.isBlank(after) || after.indexOf(task.getTaskId()) == -1) {
                        if (StrUtil.isBlank(after)) {
                            beftask.setAfterTask(task.getTaskId());
                        } else {
                            beftask.setAfterTask(after + "," + task.getTaskId());
                        }
                        Map<String, Object> updateTaskMap = new HashMap<>();
                        updateTaskMap.put("task", beftask);
                        configMapper.updateTaskDispatchConfig(updateTaskMap);
                    }
                }
            }
        }
    }

    /**
     * 修改旧的后置任务的前置任务
     */
    private void setOldAfterTask(TaskCenterRequest request) {
        String task = request.getTaskId();
        String oldBeforeTask = request.getOldBeforeTask();
        String beforeTask = request.getBeforeTask();
        if (oldBeforeTask != null && !oldBeforeTask.isEmpty()) {
            String[] oldBeforeTasks = oldBeforeTask.split(",");
            for (String taskId : oldBeforeTasks) {
                if (beforeTask != null && !beforeTask.contains(taskId)) {
                    TaskDispatchConfigDomain oldBeftask = configMapper.selectTaskDispatchConfigByPK(taskId);
                    if (oldBeftask != null) {
                        String after = oldBeftask.getAfterTask();
                        after = after.replace(task + ",", "").replace("," + task, "").replace(task, "");
                        oldBeftask.setAfterTask(after);
                        Map<String, Object> updateTaskMap = new HashMap<>();
                        updateTaskMap.put("task", oldBeftask);
                        configMapper.updateTaskDispatchConfig(updateTaskMap);
                    }
                }
            }
        }
    }

    // ==================== 批量修改任务 ====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public CommonResult<?> batchUpdate(TaskCenterRequest request) {
        if (ObjectUtil.isNotEmpty(request.getBatchList())) {
            List<Map<String, Object>> list = request.getBatchList();
            for (Map<String, Object> map : list) {
                TaskCenterRequest in = BeanUtil.mapToBean(map, TaskCenterRequest.class, true);
                this.update(in);
            }
        }
        return CommonResult.success(null);
    }

    // ==================== 删除任务 ====================

    @Override
    public CommonResult<?> delete(TaskCenterRequest request) {
        TaskDispatchConfigDomain po = new TaskDispatchConfigDomain();
        BeanUtil.copyProperties(request, po);
        TaskDispatchConfigDomain deleteTask = configMapper.selectTaskDispatchConfigByPK(po.getTaskId());
        configMapper.deleteById(po.getTaskId());
        // 删除所有等待中的该任务执行计划
        TaskDispatchExeLogExt updateParam = new TaskDispatchExeLogExt();
        if (po.getCompany() != null) {
            updateParam.setCompany(po.getCompany());
        } else if (request.getSysHead() != null) {
            updateParam.setCompany(request.getSysHead().getCompany());
        }
        updateParam.setJobId(po.getJobId());
        updateParam.setTaskId(po.getTaskId());
        updateParam.setExeStatus("I");
        updateParam.setErrInfo("任务已删除，自动忽略！");
        Map<String, Object> statusParam = BeanUtil.beanToMap(updateParam);
        exeLogMapper.updateStatusW(statusParam);
        String taskId = request.getTaskId();
        String bef = deleteTask != null ? deleteTask.getBeforeTask() : null;
        String aft = deleteTask != null ? deleteTask.getAfterTask() : null;
        Map<String, Object> param = new HashMap<>();
        if (StrUtil.isNotBlank(bef)) {
            String[] befs = bef.split(",");
            for (String s : befs) {
                TaskDispatchConfigDomain b = configMapper.selectTaskDispatchConfigByPK(s);
                if (b != null) {
                    String after = b.getAfterTask();
                    if (StrUtil.isNotBlank(after)) {
                        after = after.replace(taskId + ",", "").replace("," + taskId, "").replace(taskId, "");
                        param.put("taskId", s);
                        param.put("afterTask", after);
                        configMapper.updateAfterTask(param);
                    }
                }
            }
        }
        if (StrUtil.isNotBlank(aft)) {
            String[] afts = aft.split(",");
            for (String s : afts) {
                TaskDispatchConfigDomain a = configMapper.selectTaskDispatchConfigByPK(s);
                if (a != null) {
                    String before = a.getBeforeTask();
                    if (StrUtil.isNotBlank(before)) {
                        before = before.replace(taskId + ",", "").replace("," + taskId, "").replace(taskId, "");
                        param.put("taskId", s);
                        param.put("beforeTask", before);
                        configMapper.updateBeforeTask(param);
                    }
                }
            }
        }
        return CommonResult.success(null);
    }

    // ==================== 引擎启停操作 ====================

    @Override
    public CommonResult<?> onOrOffEngine(TaskCenterRequest request) {
        Map<String, Object> pd = new HashMap<>();
        pd.put("hostIp", request.getHostIp());
        pd.put("activeStatus", request.getActiveStatus());
        pd.put("cpsGroup", request.getCpsGroup() != null ? request.getCpsGroup() : cpsGroup);
        activeHostMapper.updateActiveHost(pd);
        TaskDispatchServiceUtil.setHostStatus(pd.get(request.getActiveStatus()) != null ? pd.get(request.getActiveStatus()).toString() : null);
        return CommonResult.success(null);
    }

    // ==================== 查看执行计划 ====================

    @Override
    public CommonResult<?> queryExeLogList(TaskCenterRequest request) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("taskId", request.getTaskId());
        queryParam.put("exeStatus", request.getExeStatus());
        queryParam.put("id", request.getId());
        if (request.getSysHead() != null) {
            queryParam.put("company", request.getSysHead().getCompany());
        }
        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<TaskDispatchExeLogDomain> list = exeLogMapper.selectExeLogList(queryParam);
        PageInfo<TaskDispatchExeLogDomain> page = new PageInfo<>(list);
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", page.getList());
        result.put("totalrecord", page.getTotal());
        return CommonResult.success(result);
    }

    // ==================== 查询作业执行信息 ====================

    @Override
    public CommonResult<?> queryJobExeLogList(TaskCenterRequest request) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("jobId", request.getJobId());
        queryParam.put("eodDate", request.getEodDate());
        queryParam.put("batchNo", request.getJobBatchNo());
        if (request.getSysHead() != null) {
            queryParam.put("company", request.getSysHead().getCompany());
        }
        List<TaskDispatchExeLogDomain> resultList = exeLogMapper.selectJobExeLogList(queryParam);
        List<TaskDispatchExeLogDomain> batchIds = exeLogMapper.selectBatchNoByJobId(queryParam);
        List<String> batchIdList = new ArrayList<>();
        batchIds.forEach(item -> batchIdList.add(item.getBatchNo()));
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", resultList);
        result.put("totalrecord", resultList.size());
        result.put("hostList", batchIdList);
        return CommonResult.success(result);
    }

    // ==================== 修改执行计划 ====================

    @Override
    public CommonResult<?> updateTaskExeLog(TaskCenterRequest request) {
        String idStr = request.getId();
        if (idStr != null) {
            String[] logIdList = idStr.split(",");
            for (String id : logIdList) {
                TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                updateLog.setId(id);
                updateLog.setExeStatus(request.getExeStatus());
                updateLog.setErrInfo(request.getErrInfo());
                Map<String, Object> updateParam = new HashMap<>();
                updateParam.put("log", updateLog);
                exeLogMapper.updateTaskDispatchExeLog(updateParam);
            }
        }
        return CommonResult.success(null);
    }

    // ==================== 手动执行任务 ====================

    @Override
    public CommonResult<?> executeJob(TaskCenterRequest request) {
        Map<String, Object> result = new HashMap<>();
        String sysEodDate = request.getExeDate();
        String taskId = request.getTaskId();
        String taskLogId = UUID.randomUUID().toString();
        try {
            TaskDispatchConfigDomain taskInfo = configMapper.selectTaskDispatchConfigByPK(taskId);
            if (taskInfo == null) {
                throw new RuntimeException("999999:" + taskId + "任务不存在");
            }
            ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
            if ("C".equals(taskInfo.getIsBefore())) {
                executor.execute(new ConditionJobThread(configMapper, exeLogMapper, taskId, true, sysEodDate, request.getParams(), taskLogId));
            } else {
                executor.execute(new ExecuteJobThread(configMapper, exeLogMapper, taskId, sysEodDate, request.getParams()));
            }
            result.put("executeMsg", "手动执行成功，请查看执行日志");
            result.put("taskLogId", taskLogId);
        } catch (Exception e) {
            log.error("远程调用异常", e);
            result.put("executeMsg", e.getMessage());
        }
        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> executeJobSync(TaskCenterRequest request) {
        Map<String, Object> result = new HashMap<>();
        String sysEodDate = request.getExeDate();
        String taskId = request.getTaskId();
        try {
            TaskDispatchConfigDomain taskInfo = configMapper.selectTaskDispatchConfigByPK(taskId);
            if (taskInfo == null) {
                throw new RuntimeException("999999:" + taskId + "任务不存在");
            }
            if ("C".equals(taskInfo.getIsBefore())) {
                result.put("executeMsg", "条件执行任务不支持同步调用，请调用/land/taskcenter/executeJob");
                result.put("executeFlag", false);
            } else {
                ExecuteJobThread thread = new ExecuteJobThread(configMapper, exeLogMapper, taskId, sysEodDate, request.getParams());
                io.github.openground.land.api.domain.JobOut out = thread.exeJob();
                result.put("executeMsg", out.getMessage());
                result.put("executeFlag", out.getSuccess());
            }
        } catch (Exception e) {
            log.error("远程调用异常", e);
            result.put("executeFlag", false);
            result.put("executeMsg", e.getMessage());
        }
        return CommonResult.success(result);
    }

    // ==================== 手工初始化作业 ====================

    @Override
    public CommonResult<?> initJob(TaskCenterRequest request) {
        Map<String, Object> out = new HashMap<>();
        String jobId = request.getJobId();
        String eodDate = request.getEodDate();
        if (ObjectUtil.isNull(eodDate)) {
            throw new RuntimeException("999999:请选择日期");
        }
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("jobId", request.getJobId());
        queryParam.put("eodDate", request.getEodDate());
        queryParam.put("batchNo", request.getJobBatchNo());
        if (request.getSysHead() != null) {
            queryParam.put("company", request.getSysHead().getCompany());
        }
        TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(jobId);
        TaskDispatchExeLogDomain no = exeLogMapper.getBatchNo(queryParam);

        Integer batchNo = Integer.parseInt(no.getBatchNo()) + 1;

        TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
        updateTask.setTaskId(task.getTaskId());
        updateTask.setBatchNo(batchNo);
        updateTask.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
        Map<String, Object> updateTaskMap = new HashMap<>();
        updateTaskMap.put("task", updateTask);
        configMapper.updateTaskDispatchConfig(updateTaskMap);

        Map<String, Object> p = new HashMap<>();
        p.put("JOB_ID", task.getTaskId());
        List<Map<String, Object>> list = exeLogMapper.findNotExeTaskPlan(p);
        if (list != null && !list.isEmpty()) {
            Map<String, Object> w = list.get(0);
            String message = "[" + task.getTaskId() + "]" + task.getTaskName() + "有未完成的任务:" + w.get("TASK_ID") + ",不能生成执行计划!";
            log.info(message);
            throw new RuntimeException("999999");
        }
        try {
            TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper, TaskDateUtil.getMachingCurrentTime());
            util.saveTaskPlan(jobId, jobId, batchNo + "", eodDate);
            util.initJob(jobId, batchNo + "", eodDate);

            // 获取作业的执行计划
            Map<String, Object> para = new HashMap<>();
            para.put("TASK_ID", jobId);
            para.put("EOD_DATE", TaskDateUtil.getSysEodDate(task.getCpsGroup()));
            para.put("BATCH_NO", batchNo);
            para.put("JOB_ID", jobId);
            TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLog4ExeAfter(para);
            if (taskPlan != null) {
                ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
                executor.execute(new StartTaskThread(configMapper, exeLogMapper, taskPlan));
            }
            out.put("executeMsg", "初始化成功,请查看作业信息");
        } catch (Exception e) {
            log.error("初始化失败:", e);
            out.put("executeMsg", "初始化失败:" + e.getMessage());
            throw new RuntimeException("999999", e);
        }
        return CommonResult.success(out);
    }

    // ==================== 手工执行任务计划 ====================

    @Override
    public CommonResult<?> exeJobPlan(TaskCenterRequest request) {
        Map<String, Object> out = new HashMap<>();
        if (ObjectUtil.isNull(request.getId())) {
            throw new RuntimeException("999999:请选择要执行的任务计划");
        }
        TaskDispatchExeLogDomain taskPlan = exeLogMapper.findExeLogById(request.getId());
        String taskId = taskPlan.getTaskId();
        // 执行子任务
        if (ObjectUtil.isNotNull(request.getSubTaskId())) {
            TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(taskId);
            Map<String, Object> paramMap = new HashMap<>();
            taskPlan.parseAttacheds();
            if (taskPlan.getAttacheds() == null || taskPlan.getAttacheds().get("attacheds") == null) {
                throw new RuntimeException("999999:执行计划中没有找到子任务日志");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attacheds = (List<Map<String, Object>>) taskPlan.getAttacheds().get("attacheds");
            for (Map<String, Object> item : attacheds) {
                if (item.get("subTaskId").equals(request.getSubTaskId())) {
                    if ("R".equals(request.getExeType())) {
                        paramMap.put("sysEodDate", taskPlan.getEodDate());
                        String params = task.getParams();
                        paramMap.put("taskPlanId", taskPlan.getId());
                        paramMap.put("subTaskId", request.getSubTaskId());
                        if (!StrUtil.isBlank(params)) {
                            try {
                                TaskDispatchServiceUtil.addParams(params, paramMap);
                            } catch (Exception e) {
                                log.error("解析参数异常", e);
                            }
                        }
                        io.github.openground.land.api.job.JobEngine job = SpringUtil.getBean(task.getTaskMethod());
                        io.github.openground.land.api.domain.JobOut jobResult;
                        try {
                            jobResult = job.execute(paramMap);
                        } catch (Exception e) {
                            log.error("执行子任务异常", e);
                            throw new RuntimeException("999999:执行子任务失败：" + e.getMessage());
                        }
                        if (jobResult.getSuccess()) {
                            item.put("exeStatus", "S");
                        } else {
                            throw new RuntimeException("999999:重新执行失败：" + jobResult.getMessage());
                        }
                        out.put("executeMsg", jobResult.getMessage());
                    } else {
                        item.put("exeStatus", "I");
                        out.put("executeMsg", "操作成功，请刷新后查看执行结果!");
                    }
                }
            }
            int succNum = 0, failNum = 0;
            for (Map<String, Object> item : attacheds) {
                if ("S".equals(item.get("exeStatus")) || "I".equals(item.get("exeStatus"))) {
                    succNum++;
                } else {
                    failNum++;
                }
            }
            taskPlan.buildAttachedStr();
            Map<String, Object> updateLogParam = new HashMap<>();
            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            updateLog.setId(request.getId());
            updateLog.setExeStatus(failNum == 0 ? "S" : "F");
            updateLog.setErrInfo("执行完成，成功:" + succNum + "个，失败:" + failNum + "个。");
            updateLog.setExtend5(taskPlan.getExtend5());
            updateLogParam.put("log", updateLog);
            exeLogMapper.updateTaskDispatchExeLog(updateLogParam);
        } else {
            // 执行主任务计划
            executePlan(request, taskId, taskPlan, out);
        }
        return CommonResult.success(out);
    }

    /**
     * 执行任务计划（主任务）
     */
    private void executePlan(TaskCenterRequest request, String taskId, TaskDispatchExeLogDomain taskPlan, Map<String, Object> out) {
        if ("R".equals(request.getExeType())) {
            TaskDispatchConfigDomain task = configMapper.selectTaskDispatchConfigByPK(taskId);
            log.info("手工重新执行......");
            log.info("更新任务" + taskId + "的执行计划[" + taskPlan.getId() + "]状态为待执行");
            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            if ("C".equals(task.getIsBefore())) {
                updateLog.setExeStatus("W");
            } else {
                updateLog.setExeStatus("P");
            }
            updateLog.setId(taskPlan.getId());
            updateLog.setExeEndTime("");
            updateLog.setExeStartTime(TaskDateUtil.getMachingCurrentTime());
            if (!"EndJob".equals(taskPlan.getTaskId())) {
                updateLog.setErrInfo("任务执行中");
            }
            updateLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
            Map<String, Object> param = new HashMap<>();
            param.put("log", updateLog);
            exeLogMapper.updateTaskDispatchExeLog(param);
            log.info("更新任务" + taskId + "的执行计划[" + taskPlan.getId() + "]状态为待执行结束");
            out.put("executeMsg", "操作成功，请刷新后查看执行结果!");
        } else {
            log.info("手工确认成功(忽略)......");
            Map<String, Object> param = new HashMap<>();
            TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
            updateLog.setId(request.getId());
            updateLog.setExeStatus("I");
            param.put("log", updateLog);
            exeLogMapper.updateTaskDispatchExeLog(param);

            TaskDispatchConfigDomain taskInfo = configMapper.selectTaskDispatchConfigByPK(taskId);
            try {
                TaskDispatchServiceUtil util = new TaskDispatchServiceUtil(configMapper, exeLogMapper);
                util.exeAfterTask(taskInfo, taskPlan);
            } catch (Exception e) {
                log.error("执行后置任务异常", e);
                throw new RuntimeException("999999", e);
            }
            out.put("executeMsg", "手工确认成功,请刷新后查看结果!");
        }
    }

    /**
     * 获取后置任务（递归，保留原逻辑）
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    private void getAfterTask(List<TaskDispatchConfigDomain> list, List<TaskDispatchConfigDomain> resultList, boolean flag, Map<String, Object> taskMap) {
        String sysEodDate = TaskDateUtil.getSysEodDate();
        for (TaskDispatchConfigDomain task : list) {
            if (task == null) {
                continue;
            }
            if ("C".equals(task.getIsBefore())) {
                String fileInfo = task.getFileInfo();
                if (fileInfo == null) {
                    task.setIsExePeriod("N");
                } else if (fileInfo.substring(0, fileInfo.indexOf("_")).equals(sysEodDate)) {
                    task.setIsExePeriod("Y");
                } else {
                    task.setIsExePeriod("N");
                }
            }
            String afterTaskIds = task.getAfterTask();
            String param = task.getParams();
            List<TaskDispatchConfigDomain> subList = new ArrayList<>();
            if (param == null || !param.contains("endJob")) {
                if (afterTaskIds != null && !afterTaskIds.trim().isEmpty()) {
                    String[] ids = afterTaskIds.split(",", -1);
                    List<String> findids = new ArrayList<>();
                    for (String id : ids) {
                        findids.add(id);
                        if (allTaskMap.containsKey(id)) {
                            subList.add(allTaskMap.get(id));
                        }
                    }
                    if (!findids.isEmpty()) {
                        subList = configMapper.getTaskInfosById(findids);
                    }
                }
                task.setIsAfter(subList != null && !subList.isEmpty() ? "Y" : "N");
                if (!taskMap.containsKey(task.getTaskId())) {
                    resultList.add(task);
                    taskMap.put(task.getTaskId(), task);
                }
                if (flag && subList != null && !subList.isEmpty()) {
                    this.getAfterTask(subList, resultList, true, taskMap);
                }
            } else {
                if (param != null && param.contains("endJob")) {
                    endJobId = task.getTaskId();
                } else {
                    resultList.add(task);
                }
                hasEndTask = true;
            }
        }
    }

    // ==================== 任务参数 ====================

    @Override
    public CommonResult<?> queryParams(TaskDispatchParamRequest request) {
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("paramName", request.getParamName());
        if (request.getSysHead() != null) {
            queryParam.put("company", request.getSysHead().getCompany());
        }
        queryParam.put("cpsGroup", request.getCpsGroup() != null ? request.getCpsGroup() : cpsGroup);
        queryParam.put("keyWord", request.getKeyWord());

        int pageIndex = request.getPageIndex() > 0 ? request.getPageIndex() : 1;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        PageHelper.startPage(pageIndex, pageSize);
        List<TaskDispatchParam> list = paramMapper.selectByPage(queryParam);
        PageInfo<TaskDispatchParam> page = new PageInfo<>(list);
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", page.getList());
        result.put("totalrecord", page.getTotal());
        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> addParam(TaskDispatchParamRequest request) {
        TaskDispatchParam po = new TaskDispatchParam();
        BeanUtil.copyProperties(request, po);
        if (request.getSysHead() != null) {
            po.setCompany(request.getSysHead().getCompany());
        }
        po.setInsertTime(new java.util.Date());
        po.setUpdateTime(new java.util.Date());
        if (StrUtil.isNotBlank(po.getPassword())) {
            PWDDes des = new PWDDes();
            po.setPassword(des.strEnc(po.getPassword()));
        }
        try {
            paramMapper.insert(po);
        } catch (Exception e) {
            log.error("新增参数异常", e);
            throw new RuntimeException("999999", e);
        }
        return CommonResult.success(null);
    }

    @Override
    public CommonResult<?> updateParam(TaskDispatchParamRequest request) {
        TaskDispatchParam po = new TaskDispatchParam();
        BeanUtil.copyProperties(request, po);
        po.setUpdateTime(new java.util.Date());
        if (StrUtil.isNotBlank(po.getPassword())) {
            TaskDispatchParam q = new TaskDispatchParam();
            q.setParamId(po.getParamId());
            TaskDispatchParam t = paramMapper.selectById(po.getParamId());
            if (t != null && !po.getPassword().equals(t.getPassword())) {
                PWDDes des = new PWDDes();
                po.setPassword(des.strEnc(po.getPassword()));
            }
        }
        try {
            paramMapper.updateById(po);
        } catch (Exception e) {
            log.error("修改参数异常", e);
            throw new RuntimeException("999999", e);
        }
        return CommonResult.success(null);
    }

    @Override
    public CommonResult<?> deleteParam(TaskDispatchParamRequest request) {
        paramMapper.deleteById(request.getParamId());
        return CommonResult.success(null);
    }

    @Override
    public CommonResult<?> queryParamOptions(TaskDispatchParamRequest request) {
        TaskDispatchParam po = new TaskDispatchParam();
        if (request.getSysHead() != null) {
            po.setCompany(request.getSysHead().getCompany());
        }
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("company", po.getCompany());
        List<TaskDispatchParam> paramList = paramMapper.selectByPage(queryParam);
        List<TaskDispatchConfigDomain> taskList = configMapper.datalistPage(new TaskDispatchConfigDomain());
        Map<String, Object> result = new HashMap<>();
        result.put("paramList", paramList);
        result.put("taskList", taskList);
        return CommonResult.success(result);
    }

    // ==================== 查询调度组列表 ====================

    @Override
    public CommonResult<?> queryCpsGroup(TaskDispatchParamRequest request) {
        List<String> cpsGroupList = new ArrayList<>();
        if (StrUtil.isNotBlank(request.getCpsGroup())) {
            // 有指定 cpsGroup 时，转大写后直接返回
            cpsGroupList.add(request.getCpsGroup().toUpperCase());
        } else {
            // 从 active_host 表查询所有活跃的 cpsGroup（去重）
            // 活跃判定：ACTIVE_STATUS = 'ON' 或 ACTIVE_TIME 在最近 10 分钟内
            Map<String, Object> param = new HashMap<>();
            param.put("activeTimeThreshold", new Date(System.currentTimeMillis() - 10 * 60 * 1000));
            cpsGroupList = activeHostMapper.selectDistinctActiveCpsGroups(param);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", cpsGroupList);
        return CommonResult.success(result);
    }

    // ==================== 分段任务 ====================

    @Override
    public CommonResult<?> executeSegment(TaskSegmentRequest request) {
        // 分段任务执行需要 SegmentThread / JobContext / IStep / StepProcessor 等域对象支持
        // 这些属于分段处理基础设施，待 Phase 4 迁移
        throw new UnsupportedOperationException("分段任务执行依赖于分段处理域模型（JobContext/SegmentThread 等），待 Phase 4 迁移");
    }

    @Override
    public CommonResult<?> querySegment(TaskSegmentRequest request) {
        String taskPlanId = request.getTaskPlanId();
        List<StepSegmentExeInfo> resultList = new ArrayList<>();

        if ("all".equals(request.getQueryType())) {
            Map<String, Object> queryParam = new HashMap<>();
            queryParam.put("taskPlanId", taskPlanId);
            List<TaskDispatchStepLog> list = stepLogMapper.selectList(queryParam);
            for (TaskDispatchStepLog t : list) {
                StepSegmentExeInfo s = new StepSegmentExeInfo();
                s.setFinished(true);
                s.setInfoMessage(t.getInfoMessage());
                s.setThreadName(t.getThreadName());
                s.setSegment(t.getSegment());
                s.setExceptionMessage(t.getExceptionMessage());
                s.setExeUrl(t.getExeUrl());
                s.setStepStatus(t.getStepStatus());
                s.setTaskPlanId(t.getTaskPlanId());
                s.setStepId(t.getStepId());
                resultList.add(s);
            }
        }
        return CommonResult.success(resultList);
    }

    @Override
    public CommonResult<?> cleanSegmentContext(TaskSegmentRequest request) {
        String taskPlanId = request.getTaskPlanId();
        if (taskPlanId != null) {
            // 分段上下文清理（StepContextManager 待 Phase 4 迁移）
            log.info("清理分段上下文: taskPlanId={}", taskPlanId);
        }
        return CommonResult.success(null);
    }

    // ==================== 任务监控 ====================

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public CommonResult<?> getCpsServiceList(TaskMonitorRequest request) {
        Map<String, Object> param = new HashMap<>();
        if (request.getCpsGroup() != null && !request.getCpsGroup().isEmpty()) {
            param.put("cpsGroup", request.getCpsGroup());
        }
        List<Map<String, Object>> activeHosts = activeHostMapper.hostListlistPage(param);
        List<Map<String, String>> urlList = new ArrayList<>();
        for (Map<String, Object> host : activeHosts) {
            Map<String, String> one = new HashMap<>();
            one.put("url", "http://" + host.get("HOST_IP"));
            one.put("server", (String) host.get("CPS_GROUP"));
            urlList.add(one);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("resultlist", urlList);
        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> threadPoolMonitor(TaskMonitorRequest request) {
        String serviceUrl = request.getServiceUrl();
        if (serviceUrl != null && !serviceUrl.isEmpty()) {
            // 转发到目标实例
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<TaskMonitorRequest> entity = new HttpEntity<>(request, headers);
                ResponseEntity<TaskMonitorResponse> response = restTemplate.postForEntity(
                        serviceUrl + "/task/taskcenter/threadPoolMonitor", entity, TaskMonitorResponse.class);
                return CommonResult.success(response.getBody());
            } catch (Exception e) {
                log.error("远程调用 threadPoolMonitor 异常: {}", serviceUrl, e);
                return CommonResult.error(ErrorCode.FAIL, "远程调用失败: " + e.getMessage());
            }
        }
        // 本地执行
        TaskMonitorResponse out = new TaskMonitorResponse();
        // 调度主线程池
        ScheduledThreadPoolExecutor executor = TaskCenterStartThread.getExecutor();
        Map<String, Object> dispatchThreadPool = new HashMap<>();
        dispatchThreadPool.put("queueSize", executor.getQueue().size() + "");
        dispatchThreadPool.put("isShutdown", executor.isShutdown() + "");
        dispatchThreadPool.put("isTerminated", executor.isTerminated() + "");
        dispatchThreadPool.put("isTerminating", executor.isTerminating() + "");
        dispatchThreadPool.put("activeCount", executor.getActiveCount() + "");
        dispatchThreadPool.put("taskCount", executor.getTaskCount() + "");
        out.setDispatchThreadPool(dispatchThreadPool);
        // 任务执行线程池
        ThreadPoolExecutor taskPoolExecutor = (ThreadPoolExecutor) TaskDispatchServiceUtil.getPoolExecutor();
        Map<String, Object> taskThreadPool = new HashMap<>();
        taskThreadPool.put("queueSize", taskPoolExecutor.getQueue().size() + "");
        taskThreadPool.put("isShutdown", taskPoolExecutor.isShutdown() + "");
        taskThreadPool.put("isTerminated", taskPoolExecutor.isTerminated() + "");
        taskThreadPool.put("isTerminating", taskPoolExecutor.isTerminating() + "");
        taskThreadPool.put("activeCount", taskPoolExecutor.getActiveCount() + "");
        taskThreadPool.put("taskCount", taskPoolExecutor.getTaskCount() + "");
        out.setTaskThreadPool(taskThreadPool);
        // JVM 内存
        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        Map<String, Object> jvmInfo = new LinkedHashMap<>();
        jvmInfo.put("HeapMemoryUsage-Max", mb.getHeapMemoryUsage().getMax() / 1024 / 1024 + "MB");
        jvmInfo.put("HeapMemoryUsage-Init", mb.getHeapMemoryUsage().getInit() / 1024 / 1024 + "MB");
        jvmInfo.put("HeapMemoryUsage-Committed", mb.getHeapMemoryUsage().getCommitted() / 1024 / 1024 + "MB");
        jvmInfo.put("HeapMemoryUsage-Used", mb.getHeapMemoryUsage().getUsed() / 1024 / 1024 + "MB");
        out.setJvmInfo(jvmInfo);
        // 线程信息
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        List<Map<String, Object>> threadList = new ArrayList<>();
        for (long id : tmx.getAllThreadIds()) {
            ThreadInfo ti = tmx.getThreadInfo(id);
            if (ti != null && ti.getThreadName() != null && ti.getThreadName().contains("DisPatch")) {
                Map<String, Object> t = new HashMap<>();
                String[] str = ti.toString().trim().split(" ");
                t.put("threadName", str[0].replaceAll("\"", ""));
                t.put("status", str.length > 2 ? str[2] : "");
                t.put("info", ti.toString().trim() + " cpu time:" + tmx.getThreadCpuTime(id) + " user time:" + tmx.getThreadUserTime(id));
                threadList.add(t);
            }
        }
        out.setThreadList(threadList);
        out.setCurrentTime(TaskDateUtil.getMachingCurrentTime());
        return CommonResult.success(out);
    }

    @Override
    public CommonResult<?> taskDashboard(TaskMonitorRequest request) {
        Map<String, Object> param = new HashMap<>();
        param.put("cpsGroup", request.getCpsGroup());
        if (request.getSysEodDate() == null || request.getSysEodDate().isEmpty()) {
            request.setSysEodDate(TaskDateUtil.getSysEodDate(request.getCpsGroup()));
        }
        param.put("sysEodDate", request.getSysEodDate());
        String activeTime = TaskDateUtil.nextOrBeforPriodTime(TaskDateUtil.getMachingCurrentTime(), -30 * 2, "s");
        param.put("activeTime", activeTime);
        param.put("currentDate", TaskDateUtil.getMachingCurrentDate());

        TaskDashboardResponse out = configMapper.queryDashboard(param);
        out.setSchCountSuccess(out.getSchCount() - out.getSchCountError());
        out.setTodaySchCountSuccess(out.getTodaySchCount() - out.getTodaySchCountError());
        out.setActivateNode(out.getTotalNode() - out.getDisableNode());
        out.setSysEodDate(request.getSysEodDate());

        // 调度报表
        param.put("sysEodDate", request.getSysEodDate());
        List<ScheduleDomain> scheduleList = configMapper.queryScheduleList(param);
        out.setScheduleList(scheduleList);

        // 待执行任务列表
        List<TaskDispatchConfigDomain> preTaskList = configMapper.queryPreTaskList(param);
        out.setPreTaskList(preTaskList);

        return CommonResult.success(out);
    }
}
