package io.github.openground.land.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.common.entity.TaskDispatchExeLogDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 执行日志表 Mapper（TASK_DISPATCH_EXE_LOG）
 * <p>继承 BaseMapper 获得通用 CRUD</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskDispatchExeLogMapper extends BaseMapper<TaskDispatchExeLogDomain> {

    // ==================== 业务查询（手写 XML） ====================

    List<TaskDispatchExeLogDomain> selectWaitDispatchTaskByHostIp(Map<String, Object> param);

    int insertTaskDispatchExeLog(TaskDispatchExeLogDomain log);

    int updateTaskDispatchExeLog(Map<String, Object> param);

    List<TaskDispatchExeLogDomain> findExeLog4checkBefore(Map<String, Object> param);

    List<TaskDispatchExeLogDomain> findExeLog4checkBeforeIgnore(Map<String, Object> param);

    TaskDispatchExeLogDomain findExeLog4ExeAfter(Map<String, Object> param);

    TaskDispatchExeLogDomain findExeLogById(@Param("ID") String id);

    List<Map<String, Object>> findNotExeTaskPlan(Map<String, Object> param);

    int deleteTaskPlanByPrimaryKey(@Param("ID") String id);

    List<TaskDispatchExeLogDomain> selectExeLogList(Map<String, Object> param);

    List<TaskDispatchExeLogDomain> selectJobExeLogList(Map<String, Object> param);

    int updateStatus(Map<String, Object> param);

    int updateStatusW(Map<String, Object> param);

    TaskDispatchExeLogDomain getBatchNo(Map<String, Object> param);

    List<TaskDispatchExeLogDomain> selectBatchNoByJobId(Map<String, Object> param);

    /** 调用存储过程 */
    void callProcedure(Map<String, Object> param);
}
