package io.github.openground.land.api.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Title: JobContext</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 * @Date 2022/9/22 9:59
 */
@Data
public class JobContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行计划ID
     */
    public static final String TASK_PLAN_ID = "taskPlanId";
    /**
     * 分段执行ID
     */
    public static final String SEGMENT_EXEID = "segmentExeId";

    /**
     * 分段数
     */
    public static final String JOB_STEP = "jobStep";

    /**
     * 分段数据条数
     */
    public static final String SEGMENT_SIZE = "segmentSize";

    /**
     * 事务条数
     */
    public static final String CHUNK_SIZE = "chunkSize";

    /**
     * 每页条数
     */
    public static final String PAGE_SIZE = "pageSize";

    /**
     * 分段job对象
     */
    public static final String STEP = "step";

    public static final String SUCCESS = "success";

    public static final String MESSAGE = "message";

    public static final String STEP_EXCEPTION = "stepException";

    /**
     * 分段执行ID
     */
    public static final String SEGMENT = "segment";

    private final Map<String, Object> jobParam = new ConcurrentHashMap();

    private final Map<String, Object> stepParam = new ConcurrentHashMap();

    private final Map<String, Object> executorContext = new ConcurrentHashMap();

    private boolean finished = false;

    private StepSegmentExeInfo stepSegmentExeInfo;

    private String exeMessage;

    public void clear() {
        jobParam.clear();
        stepParam.clear();
        executorContext.clear();
    }

}
