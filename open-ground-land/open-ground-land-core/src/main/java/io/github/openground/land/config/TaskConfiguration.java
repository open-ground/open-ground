package io.github.openground.land.config;

import io.github.openground.land.common.util.TaskDateUtil;
import io.github.openground.land.core.TaskCenterStartThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@AutoConfiguration
@AutoConfigureAfter(LandDataSourceConfig.class)
@EnableConfigurationProperties(TaskConfig.class)
@EnableScheduling
public class TaskConfiguration {

    private final TaskConfig taskConfig;

    public TaskConfiguration(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
    }

    @Bean(initMethod = "init",destroyMethod = "destroy")
    public TaskCenterStartThread taskCenterStartThread() {
        log.info("TaskCenterStartThread init...");
        return new TaskCenterStartThread();
    }

    @Bean(initMethod = "init")
    public TaskDateUtil dateUtil() {
        log.info("io.github.openground.land.common.util.TaskDateUtil init...");
        return new TaskDateUtil();
    }

    @Bean(name = "landTaskExecutor")
    public AsyncTaskExecutor threadPoolTaskExecutor() {
        TaskConfig.StepPool pool = taskConfig.getStepPool();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCoreSize());
        executor.setMaxPoolSize(pool.getMaxSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix("STEP-WORK-");
        executor.initialize();
        log.info("子任务线程池初始化: core={}, max={}, queue={}", pool.getCoreSize(), pool.getMaxSize(), pool.getQueueCapacity());
        return executor;
    }


}
