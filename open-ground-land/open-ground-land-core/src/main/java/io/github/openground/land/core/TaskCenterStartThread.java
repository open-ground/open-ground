package io.github.openground.land.core;

import io.github.openground.land.common.util.Tools;
import io.github.openground.land.config.TaskConfig;
import io.github.openground.land.mapper.TaskDispatchActiveHostMapper;
import io.github.openground.land.mapper.TaskDispatchConfigMapper;
import io.github.openground.land.mapper.TaskDispatchExeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务中心启动线程,系统启动时加载此对象,并且调用初始化方法启动主线程
 * <p>调度引擎不需要事务管理，所有 SQL 操作由 MyBatis 自动提交。</p>
 *
 * @author jack.zhang
 * @version v_1.0
 * @date 2017年3月10日 下午3:59:05
 */
@Slf4j
public class TaskCenterStartThread implements InitializingBean {

    private boolean cps_start = false; //cps引擎是否初始化

    @Value("${spring.application.name}")
    private String cpsGroup;

    private int scanningPeriod = 30;// 扫描周期,默认30秒

    private int conditionScanningPeriod = 60;// 扫描周期,默认30秒

    private long delay = 1 * 60 * 1000L; //延迟调用时间，单位毫秒,默认1分钟


    @Autowired
    private TaskConfig task;

    private static ScheduledThreadPoolExecutor executor;

    @Autowired
    private TaskDispatchActiveHostMapper activeHostMapper;

    @Autowired
    private TaskDispatchConfigMapper configMapper;

    @Autowired
    private TaskDispatchExeLogMapper exeLogMapper;

    /**
     * <p>Description: 初始化</P>
     * 要保证只能被初始化一次，否则调度线程池会重复初始化，任务会有问题
     *
     * @param
     * @return void
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/12 18:21
     */
    public void init() {

        Map<String, Object> taskCenter = task.getTaskCenter();
        Map<String, Object> dispatchCenter = task.getDispatchCenter();
        // 调度中心参数
        int schThreadSize = dispatchCenter.containsKey("schThreadSize") ? (int) dispatchCenter.get("schThreadSize") : 2;
        // 任务中心参数
        int corePoolSize = taskCenter.containsKey("corePoolSize") ? (int) taskCenter.get("corePoolSize") : 10;
        int maxPoolSize = taskCenter.containsKey("maxPoolSize") ? (int) taskCenter.get("maxPoolSize") : 100;
        int queueSize = taskCenter.containsKey("queueSize") ? (int) taskCenter.get("queueSize") : 1000;
        // 2024-02-26 update by jack.zhang 增加主机类型参数，若ip不固定，一直变的情况下，可以使用主机名当作节点唯一键
        String hostType = taskCenter.containsKey("hostType") ? (String) taskCenter.get("hostType") : "ip";
        if("HOSTNAME".equalsIgnoreCase(hostType)){
            String hostName = System.getProperty("user.name");
            TaskDispatchServiceUtil.setCpsHostIp(hostName);
        } else {
            TaskDispatchServiceUtil.setCpsHostIp(Tools.getServerIp());
        }
        // update by jack.zhang end
        TaskDispatchServiceUtil.setTaskConfig(task);
        log.info("cps start flag is : " + cps_start);
        String cpsGroup = this.cpsGroup.toUpperCase();
        if (cps_start) {
            log.info("任务中心模块准备启用" + schThreadSize + "个计时器线程,用来处理调度平台...");
            this.initScheduledThreadPool(schThreadSize);
            TaskDispatchServiceUtil.setPoolExecutor(this.initTaskThreadPool(corePoolSize, maxPoolSize, queueSize));
            executor.scheduleAtFixedRate(new TaskDispatchMainThread(activeHostMapper, configMapper, exeLogMapper, scanningPeriod, cpsGroup), delay, scanningPeriod * 1000L, TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(new TaskDispatchConditionThread(activeHostMapper, configMapper, exeLogMapper, conditionScanningPeriod, cpsGroup), delay, conditionScanningPeriod * 1000L, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * <p>Description: 初始化调度线程池</P>
     *
     * @param schThreadSize
     * @return void
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/12 17:41
     */
    private void initScheduledThreadPool(int schThreadSize) {
        executor = new ScheduledThreadPoolExecutor(schThreadSize,
                new ThreadFactory() {
                    final AtomicInteger threadNum = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable runnable) {
                        String name = "DisPatch-Main-" + threadNum.getAndIncrement();
                        SecurityManager s = System.getSecurityManager();
                        ThreadGroup threadGroup = (s == null) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
                        Thread ret = new Thread(threadGroup, runnable, name, 0);
                        ret.setDaemon(false);
                        return ret;
                    }
                });
    }

    /**
     * <p>Description: 初始化任务线程池</P>
     *
     * @param minSize
     * @param maxSize
     * @param queueSize
     * @return java.util.concurrent.ExecutorService
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/12 17:41
     */
    private ExecutorService initTaskThreadPool(int minSize, int maxSize, int queueSize) {
        ExecutorService poolExecutor = new ThreadPoolExecutor(minSize, maxSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(queueSize),
                new ThreadFactory() {
                    final AtomicInteger threadNum = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable runnable) {
                        String name = "DisPatch-Task-" + threadNum.getAndIncrement();
                        SecurityManager s = System.getSecurityManager();
                        ThreadGroup threadGroup = (s == null) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
                        Thread ret = new Thread(threadGroup, runnable, name, 0);
                        ret.setDaemon(false);
                        return ret;
                    }
                });
        return poolExecutor;
    }

    public void destroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        log.info("任务中心模块已关闭全部计时器线程");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cps_start = (boolean) task.getDispatchCenter().getOrDefault("cpsInit", false);
        String cpsGroup = this.cpsGroup.toUpperCase();
        TaskDispatchServiceUtil.setCpsGroup(cpsGroup);
        if (cps_start) {
            TaskDispatchServiceUtil util = new TaskDispatchServiceUtil();
            util.updateTaskDispatchConfig(configMapper, null, cpsGroup);
        }
    }

    public static ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }
}
