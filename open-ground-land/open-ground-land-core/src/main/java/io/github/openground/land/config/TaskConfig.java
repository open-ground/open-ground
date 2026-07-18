package io.github.openground.land.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: TaskConfig</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 * @Date 2020/6/3 23:28
 * @Version 3.0.0
 */
@Data
@ConfigurationProperties(prefix = "task")
public class TaskConfig {

    /**
     * <p>Description: 任务调度中心</P>
     *
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2020/6/4 15:09
     * @param null
     * @return
     */
    private Map<String, Object> taskCenter = new HashMap<>();

    /**
     * <p>Description: 调度中心配置</P>
     *
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/12 18:32
     * @param null
     * @return
     */
    private Map<String, Object> dispatchCenter = new HashMap<>();

    /** 活跃主机心跳超时秒数（默认 180 秒 = 3 分钟） */
    private int activeHostTimeoutSeconds = 180;

    /** 子任务线程池配置（数据交换、批处理、脚本等子任务共享） */
    private StepPool stepPool = new StepPool();

    @Data
    public static class StepPool {
        private int coreSize = 30;
        private int maxSize = 100;
        private int queueCapacity = 200;
    }
}
