package io.github.openground.land.core;

import io.github.openground.base.utils.SpringUtil;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import io.github.openground.land.service.ActiveHostService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 任务调度任务条件检查线程
 *
 * @author jack.zhang
 * @version v 1.0
 * @date 2019年3月8日 上午9:22:18
 */
@Slf4j
public class TaskDispatchConditionThread implements Runnable {

    private int scanningPeriod;
    private TaskDispatchActiveHostMapper activeHostMapper;
    private TaskDispatchConfigMapper configMapper;
    private TaskDispatchExeLogMapper exeLogMapper;
    private String cpsGroup;

    public TaskDispatchConditionThread(TaskDispatchActiveHostMapper activeHostMapper,
                                       TaskDispatchConfigMapper configMapper,
                                       TaskDispatchExeLogMapper exeLogMapper,
                                       int scanningPeriod, String cpsGroup) {
        this.activeHostMapper = activeHostMapper;
        this.configMapper = configMapper;
        this.exeLogMapper = exeLogMapper;
        this.scanningPeriod = scanningPeriod;
        this.cpsGroup = cpsGroup;
    }

    @Override
    public void run() {

        try {
            String currentTime = TaskDateUtil.getMachingCurrentTime(); // 使用机器时间
            log.debug("作业调度条件检查线程已启动，扫描周期：" + scanningPeriod + "秒, 执行时间：" + currentTime);

            if (!this.getActiveStatus()) {
                log.debug("当前主机引擎开关未开启，任务调度条件检查不执行");
                return;
            }

            this.conditionJob();
        } catch (Throwable e) {
            log.error("调度引擎执行异常", e);
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
        if (TaskDispatchServiceUtil.getHostStatus() != null) {
            hostStatus = TaskDispatchServiceUtil.getHostStatus();
        } else {
            String hostIp = TaskDispatchServiceUtil.getCpsHostIp();
            ActiveHostService activeHostService = SpringUtil.getBean(ActiveHostService.class);
            Map<String, Object> map = activeHostService.getHostStatus(hostIp, cpsGroup);
            if (map != null && map.containsKey("ACTIVE_STATUS")) {
                hostStatus = (String) map.get("ACTIVE_STATUS");
                TaskDispatchServiceUtil.setHostStatus(hostStatus);
            }
        }
        if ("ON".equals(hostStatus)) {
            return true;
        }
        return false;
    }

    /**
     * 执行条件依赖任务
     *
     * @throws Exception
     * @description:
     * @author open-ground
     * @date 2018年12月1日 下午6:01:41
     */
    @SuppressWarnings("unchecked")
    private void conditionJob() throws Exception {
        log.info("开始执行条件依赖任务");
        String hostIp = TaskDispatchServiceUtil.getCpsHostIp();
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("HOST_IP", hostIp);
        param.put("CPS_GROUP", cpsGroup);
        // 获取同一任务中最早的，还未执行的任务
        List<TaskDispatchConfigDomain> list = configMapper.findConditionTask(param);
        for (TaskDispatchConfigDomain task : list) {
            ExecutorService executor = TaskDispatchServiceUtil.getPoolExecutor();
            executor.execute(new ConditionJobThread(configMapper, exeLogMapper, task));
            // ConditionJobThread job = new ConditionJobThread(dao, task);
            // job.start();
            Thread.sleep(3000);
        }
        log.info("执行条件依赖任务结束");

    }
}
