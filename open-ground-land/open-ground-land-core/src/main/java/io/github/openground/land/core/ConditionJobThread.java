package io.github.openground.land.core;

import ch.ethz.ssh2.Connection;
import cn.hutool.core.exceptions.ExceptionUtil;
import com.github.pagehelper.util.StringUtil;
import com.jcraft.jsch.ChannelSftp;
import io.github.openground.base.utils.SpringUtil;
import io.github.openground.common.keygen.KeyGenerator;
import io.github.openground.land.api.domain.JobOut;
import io.github.openground.land.api.job.JobEngine;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import io.github.openground.land.common.entity.TaskDispatchParam;
import io.github.openground.land.common.util.FtpUitl;
import io.github.openground.land.common.util.PWDDes;
import io.github.openground.land.common.util.PageData;
import io.github.openground.land.common.util.SftpUtil;
import io.github.openground.land.common.util.ShellUtil;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.mapper.TaskDispatchParamMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.MDC;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 条件依赖任务线程
 *
 * @author jack.zhang
 * @version v 1.0
 * @date 2019年3月11日 下午12:50:46
 */
@Slf4j
public class ConditionJobThread extends Thread {

    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private TaskDispatchParamMapper paramMapper;

    private TaskDispatchConfigDomain task;

    private boolean isExecute;

    private String sysEodDate;
    private String fileName;
    private String localPath;
    private String taskLogId;
    private String exeParams;

    /**
     * 自动执行
     *
     * @param dao
     * @param task TaskDispatchConfigDomain中必须包含ID属性
     */
    public ConditionJobThread(TaskDispatchConfigMapper configMapper, TaskDispatchExeLogMapper exeLogMapper, TaskDispatchConfigDomain task) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.paramMapper = SpringUtil.getBean("taskDispatchParamMapper");
        this.task = task;
        this.isExecute = false;
        this.exeParams = null;
    }

    /**
     * <p>Description: 可传递参数构造方法</P>
     *
     * @param dao
     * @param taskId
     * @param isExecute
     * @param sysEodDate
     * @param exeParams
     * @return
     * @Author:jack.zhang
     * @Date 2022/8/10 17:40
     */
    public ConditionJobThread(TaskDispatchConfigMapper configMapper, TaskDispatchExeLogMapper exeLogMapper, String taskId, boolean isExecute, String sysEodDate, String exeParams) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.isExecute = isExecute;
        this.sysEodDate = sysEodDate;
        this.exeParams = exeParams;
        task = configMapper.selectTaskDispatchConfigByPK(taskId);
    }

    /**
     * <p>Description: 可传递参数构造方法</P>
     *
     * @param dao
     * @param taskId
     * @param isExecute
     * @param sysEodDate
     * @param exeParams
     * @param taskLogId since 20260525
     * @return
     * @Author:jack.zhang
     * @Date 2022/8/10 17:40
     */
    public ConditionJobThread(TaskDispatchConfigMapper configMapper, TaskDispatchExeLogMapper exeLogMapper, String taskId, boolean isExecute, String sysEodDate, String exeParams, String taskLogId) {
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.isExecute = isExecute;
        this.sysEodDate = sysEodDate;
        this.exeParams = exeParams;
        this.taskLogId = taskLogId;
        task = configMapper.selectTaskDispatchConfigByPK(taskId);
    }

    @Override
    public void run() {
        PWDDes des = new PWDDes();
        TaskDispatchParam param;
        String remotePath = "";
        String localPath = "";
        String fileName = "";
        String fileEncoding = "";
        String logfileName = "";
        String taskPlanId = task.getId();
        TaskDispatchExeLogDomain taskPlan = null;
        // 获取条件参数
        try {
            // 检查文件是否到位
            if (taskPlanId != null) {
                this.taskLogId = taskPlanId;
                taskPlan = exeLogMapper.findExeLogById(taskPlanId);
                if (taskPlan.getFileName() != null) {
                    // 文件已到位，检查前置任务是否已经执行结束
                    String filepath = taskPlan.getExtend1();
                    File file = new File(filepath, taskPlan.getFileName());
                    if (file.exists() && file.isFile()) {
                        this.exeTaskPlan(taskPlan);
                        return;
                    } else {
                        log.info("task：" + task.getTaskName() + "文件" + file.getPath() + "不存在,重新获取文件");
                    }
                }
            }
            Map<String, Object> map = new HashMap<>();
            if (StringUtil.isEmpty(task.getConditionParam())) {
                log.warn("task：" + task.getTaskName() + " 条件依赖参数未维护");
                return;
            }
            map.put("paramId", task.getConditionParam());
            param = paramMapper.selectByPrimaryKey(map);
            if (param == null) {
                log.warn("task：" + task.getTaskName() + " 条件依赖参数维护有误，请检查是否存在");
                return;
            }
            remotePath = param.getRemotePath();
            localPath = param.getLocalPath();
            if (!isExecute) {
                if (taskPlan == null) {
                    sysEodDate = TaskDateUtil.getSysEodDate(task.getCpsGroup());
                } else {
                    sysEodDate = taskPlan.getEodDate();
                }
            }
            // 按规则处理路径
            if ("Y".equals(param.getIsDynamicPath())) {
                // 拼接日期
                if (remotePath.contains(":")) {
                    remotePath += sysEodDate + "\\";
                } else {
                    remotePath += sysEodDate + "/";
                }
                if (localPath.contains(":")) {
                    localPath += sysEodDate + "\\";
                } else {
                    localPath += sysEodDate + "/";
                }
            }
            // 按规则处理文件名
            fileName = param.getFileName();
            if ("Y".equals(param.getIsDynameName())) {
                fileName = MessageFormat.format(fileName, sysEodDate);
            }
            this.fileName = fileName;
            this.localPath = localPath;
            fileEncoding = param.getEncoding();

            // v4.1.0 update by jack.zhang 20220208
            /** 通过参数获取检测文件，若参数为空，则按文件名称检测 */
            if (StringUtils.isEmpty(param.getExtend3())) {
                logfileName = fileName;
            } else {
                if ("Y".equals(param.getIsDynameName())) {
                    logfileName = MessageFormat.format(param.getExtend3(), sysEodDate);
                } else {
                    logfileName = param.getExtend3();
                }
            }
            // v4.1.0 update by jack.zhang 20220208 end
        } catch (Exception e1) {
            log.error("获取参数异常", e1);
            return;
        }

        // 根据参数登录远程服务器
        try {
            boolean exist = false;
            boolean flag = false;

            if (param.getHostIp().equals("127.0.0.1") || param.getHostIp().equalsIgnoreCase("localhost")) {
                //文件在本地
                log.info("检查本地目录:" + remotePath + "是否存在文件: " + fileName);
                if (!remotePath.endsWith(File.separator)) {
                    remotePath = remotePath + File.separator;
                }
                if (!localPath.endsWith(File.separator)) {
                    localPath = localPath + File.separator;
                }
                File remoteLogfile = new File(remotePath + logfileName);
                if (remoteLogfile.exists() && remoteLogfile.isFile()) {
                    //日志文件已存在，检查数据文件
                    File remoteDatafile = new File(remotePath + fileName);
                    if (remoteDatafile.exists() && remoteDatafile.isFile()) {
                        //数据文件已存在，将其拷贝到localpath中
                        if (remotePath.equals(localPath)) {
                            flag = true;
                        } else {
                            File dir = new File(localPath);
                            if (!dir.exists() || !dir.isDirectory()) {
                                dir.mkdirs();
                            }
                            FileUtils.copyFile(remoteDatafile, new File(dir, fileName));
                            flag = true;
                        }
                    } else {
                        //数据文件不存在
                        flag = false;
                    }
                    exist = true;
                }
            } else {
                log.info("连接远程主机：" + param.getHostIp() + " 端口：" + param.getPort());
                if ("22".equals(param.getPort())) {
                    ShellUtil shellUtil = new ShellUtil();
                    Connection connection = shellUtil.getConnection(param.getUsername(), des.strDec(param.getPassword()), param.getHostIp(), 22);
                    if (connection != null) {
                        exist = shellUtil.fileIsExist(connection, remotePath, logfileName); // 检查log文件是否存在
                        if (exist) {
                            log.info(task.getTaskName() + "文件名：" + logfileName + "存在");
                            flag = shellUtil.downloadFile(connection, remotePath, fileName, localPath);
                        }
                    } else {
                        if (isExecute) {
                            this.saveExeLog("I", "SFTP可能连接失败了，请检查");
                        } else {
                            TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
                            update.setId(taskPlanId);
                            update.setExeStatus("F");
                            update.setErrInfo("FTP可能连接失败了，请检查");
                            Map<String, Object> updateParam = new HashMap<String, Object>();
                            updateParam.put("log", update);
                            exeLogMapper.updateTaskDispatchExeLog(updateParam);
                        }
                        return;
                    }
                    shellUtil.closeConnection(connection);
                } else if ("SFTP".equals(param.getPort())) {
                    SftpUtil sftpUtil = SftpUtil.getInstance();
                    ChannelSftp channel = sftpUtil.getChannelSftp(param.getUsername(), des.strDec(param.getPassword()), param.getHostIp(), 22);
                    exist = sftpUtil.fileIsExist(channel, remotePath, fileName);
                    if (exist) {
                        flag = sftpUtil.downloadFile(channel, remotePath, fileName, localPath);
                    }
                    sftpUtil.closeChannel(channel);
                } else {
                    FtpUitl ftpUitl = FtpUitl.getInstance();
                    FTPClient ftpClient = ftpUitl.getFtpClient(param.getUsername(), des.strDec(param.getPassword()), param.getHostIp(), 21, param.getEncoding());
                    if (ftpClient != null) {
                        exist = ftpUitl.fileIsExist(ftpClient, remotePath, logfileName); // 检查log文件是否存在
                        if (exist) {
                            log.info(task.getTaskName() + "文件名：" + logfileName + "存在");
                            flag = ftpUitl.downloadFile(ftpClient, remotePath, fileName, localPath);
                        }
                        ftpUitl.closeftp(ftpClient);
                    } else {
                        if (isExecute) {
                            this.saveExeLog("I", "FTP可能连接失败了，请检查");
                        } else {
                            TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
                            update.setId(taskPlanId);
                            update.setExeStatus("F");
                            update.setErrInfo("FTP可能连接失败了，请检查");
                            Map<String, Object> updateParam = new HashMap<String, Object>();
                            updateParam.put("log", update);
                            exeLogMapper.updateTaskDispatchExeLog(updateParam);
                        }
                        return;
                    }
                }
            }

            if (exist) {
                if (!flag) {
                    log.error("数据文件获取失败, 不执行");
                    TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
                    update.setId(taskPlanId);
                    update.setExeStatus("W");
                    update.setErrInfo("数据文件获取失败, 不执行");
                    Map<String, Object> updateParam = new HashMap<String, Object>();
                    updateParam.put("log", update);
                    exeLogMapper.updateTaskDispatchExeLog(updateParam);
                    return;
                }
            } else {
                log.info("数据文件" + fileName + "文件不存在,[" + task.getTaskName() + "]执行条件不满足");
                if ("R".equals(task.getTaskIsRunning())) { // 重新执行
                    TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
                    update.setId(taskPlanId);
                    update.setExeStatus("W");
                    update.setErrInfo("文件未到位，待执行!");
                    Map<String, Object> updateParam = new HashMap<String, Object>();
                    updateParam.put("log", update);
                    exeLogMapper.updateTaskDispatchExeLog(updateParam);
                }
                if (isExecute) {
                    this.saveExeLog("I", fileName + "文件不存在,[" + task.getTaskName() + "]手动执行条件不满足");
                }
                return;
            }
            log.info("成功获取数据文件：" + fileName);
            // 更新任务配置中的文件信息
            TaskDispatchConfigDomain updateTask = new TaskDispatchConfigDomain();
            updateTask.setTaskId(task.getTaskId());
            int batchNo = TaskDispatchServiceUtil.getBatchNo(task.getJobId(), TaskDateUtil.getSysEodDate(task.getCpsGroup()), task.getCompany(), exeLogMapper);
            updateTask.setBatchNo(batchNo);
            updateTask.setUpdateTime(TaskDateUtil.getMachingCurrentTime());
            updateTask.setFileInfo(sysEodDate + "_" + fileName);
            Map<String, Object> updateTaskMap = new HashMap<String, Object>();
            updateTaskMap.put("task", updateTask);
            configMapper.updateTaskDispatchConfig(updateTaskMap);

            Map<String, Object> updateParam = new HashMap<String, Object>();
            if (isExecute) {
                this.saveExeLog("P", "");

                TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
                update.setId(this.taskLogId);
                update.setExeStatus("R");
                update.setPlanStartTime("手动执行");
                update.setEodDate(sysEodDate);
                update.setExeStartTime(TaskDateUtil.getMachingCurrentTime());
                updateParam.put("log", update);
                exeLogMapper.updateTaskDispatchExeLog(updateParam);

                Map<String, Object> paramsMap = new HashMap<String, Object>();
                JobEngine job = SpringUtil.getBean(task.getTaskMethod());
                paramsMap.put("fileName", fileName);
                paramsMap.put("filePath", localPath);
                paramsMap.put("fileEncoding", fileEncoding);
                paramsMap.put("sysEodDate", sysEodDate);
                paramsMap.put("conditionParam", param);
//                TaskDispatchExeLogDomain currTaskPlan = exeLogMapper.findExeLogById(this.taskLogId);
                paramsMap.put("taskPlanId", this.taskLogId);
                paramsMap.put("taskConfig", task);

                String params = task.getParams(); // 处理传入参数
                if (!StringUtils.isEmpty(params)) {
                    TaskDispatchServiceUtil.addParams(params, paramsMap);
                }
                // 20220810 update by jack.zhang 处理手动执行传递参数
                if (!StringUtils.isEmpty(this.exeParams)) {
                    try {
                        TaskDispatchServiceUtil.addParams(this.exeParams, paramsMap);
                    } catch (Exception e) {
                        log.error("处理参数异常", e);
                    }
                }
                // update by jack.zhang end

                JobOut out = new JobOut();
                String message = "";
                String type = "";
                try {
                    MDC.put("taskId", taskPlanId);
                    MDC.put("taskPlanId", this.taskLogId);
                    out = job.execute(paramsMap);
                    boolean success = out.getSuccess();
                    message = out.getMessage();
                    type = success ? "S" : "F";
                } catch (Exception e) {
                    message = ExceptionUtil.stacktraceToString(e, 1000);
                    type = "F";
                    log.error("任务执行异常：", e);
                }
                MDC.remove("taskId");
                MDC.remove("taskPlanId");
                update.setErrInfo(message);
                update.setExeStatus(type);
                update.setExeStartTime(null);
                update.setExeEndTime(TaskDateUtil.getMachingCurrentTime());
                updateParam.put("log", update);
                exeLogMapper.updateTaskDispatchExeLog(updateParam);
            } else {
                // 更新执行计划中的文件信息 并准备执行任务
                TaskDispatchExeLogDomain updateLog = new TaskDispatchExeLogDomain();
                updateLog.setId(taskPlanId);
                updateLog.setFileName(fileName);
                updateLog.setExtend1(localPath);
                updateLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
                updateParam.put("log", updateLog);
                exeLogMapper.updateTaskDispatchExeLog(updateParam);
                if (taskPlan != null) {
                    taskPlan = exeLogMapper.findExeLogById(taskPlanId);
                    // update by jack.zhang 防止数据库不同步，查询不到，此处直接透传参数
                    taskPlan.setFileName(fileName);
                    taskPlan.setExtend1(localPath);
                    this.exeTaskPlan(taskPlan);
                    return;
                }
            }
        } catch (Exception e) {
            log.error("系统异常", e);
            Map<String, Object> updateParam = new HashMap<String, Object>();
            TaskDispatchExeLogDomain update = new TaskDispatchExeLogDomain();
            update.setId(this.taskLogId);
            update.setExeStatus("F");
            update.setErrInfo(ExceptionUtil.stacktraceToString(e, 1000));
            update.setExeEndTime(TaskDateUtil.getMachingCurrentTime());
            updateParam.put("log", update);
            try {
                exeLogMapper.updateTaskDispatchExeLog(updateParam);
            } catch (Exception e1) {
                log.error("系统异常", e);
            }
        }
    }

    /**
     * 插入执行计划
     *
     * @param status
     * @param errInfo
     * @throws Exception
     * @author jack.zhang
     * @data 2019年1月17日 下午8:35:28
     */
    private void saveExeLog(String status, String errInfo) throws Exception {
        TaskDispatchExeLogDomain taskLog = new TaskDispatchExeLogDomain();
        // 生成执行计划
        if (ObjectUtils.isEmpty(this.taskLogId)) {
            this.taskLogId = KeyGenerator.getBusinessKey("TASK_LOG");
        }
        taskLog.setId(this.taskLogId);
        taskLog.setTaskId(task.getTaskId());
        taskLog.setJobId(task.getTaskId());
        taskLog.setPlanStartTime(TaskDateUtil.getMachingCurrentTime());
        taskLog.setExeStartTime("");
        taskLog.setExeEndTime("");
        taskLog.setExeStatus(status); // 状态为待处理
        taskLog.setErrInfo(errInfo);
        taskLog.setExeCurHostIp(TaskDispatchServiceUtil.getCpsHostIp());
        taskLog.setMtTime(TaskDateUtil.getMachingCurrentTime());
        taskLog.setBatchNo("" + task.getBatchNo());
        taskLog.setFileName(fileName);
        taskLog.setExtend1(localPath);
        taskLog.setCompany(task.getCompany());
        exeLogMapper.insertTaskDispatchExeLog(taskLog);
    }

    @SuppressWarnings("unchecked")
    private void exeTaskPlan(TaskDispatchExeLogDomain taskPlan) throws Exception {
        String beforeTask = task.getBeforeTask();
        String[] tasks = beforeTask.split(",");
        // 判断该任务的所有前置任务是否执行结束
        PageData p = new PageData();
        p.put("EOD_DATE", taskPlan.getEodDate());
        p.put("BATCH_NO", taskPlan.getBatchNo());
        p.put("JOB_ID", taskPlan.getJobId());
        p.put("tasks", tasks);
        List<String> status = new ArrayList<String>();
        status.add("S");
        status.add("I");
        p.put("status", status);
        // 根据作业编码、跑批日期、批次号和任务编码 查询执行成功的任务数，和前置任务数做比较，若相同，则任务前置任务都执行成功
        List<TaskDispatchExeLogDomain> beforeList = exeLogMapper.findExeLog4checkBefore(p);
        log.info("任务[" + task.getTaskId() + "]有" + tasks.length + "个前置任务，已经执行成功" + beforeList.size() + "个");
        if (tasks.length == beforeList.size()) {
            log.info("任务[" + task.getTaskId() + "]满足执行条件，准备开始掉起");
            // 改为线程池的方式
            ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
            executor.execute(new StartTaskThread(configMapper, exeLogMapper, taskPlan));
        } else {
            log.info("任务[" + task.getTaskId() + "]前置任务未执行结束，不满足执行条件");
        }
    }
}
