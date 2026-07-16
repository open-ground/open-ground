package io.github.openground.land.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.common.entity.TaskDispatchConfigDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 任务配置表 Mapper（TASK_DISPATCH_CONFIG）
 * <p>继承 BaseMapper 获得通用 CRUD：insert, deleteById, updateById, selectById, selectList, selectCount 等</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskDispatchConfigMapper extends BaseMapper<TaskDispatchConfigDomain> {

    // ==================== 业务查询（手写 XML） ====================

    List<TaskDispatchConfigDomain> selectTaskByInfo(Map<String, Object> param);

    List<TaskDispatchConfigDomain> selectValidInfo(Map<String, Object> param);

    TaskDispatchConfigDomain selectTaskDispatchConfigByPK(@Param("taskId") String taskId);

    List<TaskDispatchConfigDomain> selectTaskDispatchConfigByJobId(@Param("jobId") String jobId);

    int updateTaskDispatchConfig(Map<String, Object> param);

    List<TaskDispatchConfigDomain> datalistPage(TaskDispatchConfigDomain query);

    List<TaskDispatchConfigDomain> getTaskInfosById(@Param("list") List<String> taskIds);

    List<TaskDispatchConfigDomain> getAfterTaskInfo(@Param("taskId") String taskId);

    List<TaskDispatchConfigDomain> findTaskByStatus0(Map<String, Object> param);

    List<TaskDispatchConfigDomain> findConditionTask(Map<String, Object> param);

    List<TaskDispatchConfigDomain> selectTaskByIP(Map<String, Object> param);

    int updateBeforeTask(Map<String, Object> param);

    int updateAfterTask(Map<String, Object> param);

    int runSql(@Param("sql") String sql);

    void callProcedure(Map<String, Object> param);

    // ==================== 仪表盘统计 ====================

    /** 仪表盘统计 */
    io.github.openground.land.api.dto.TaskDashboardResponse queryDashboard(Map<String, Object> param);

    /** 调度报表 */
    List<io.github.openground.land.common.entity.ScheduleDomain> queryScheduleList(Map<String, Object> param);

    /** 待执行任务列表 */
    List<TaskDispatchConfigDomain> queryPreTaskList(Map<String, Object> param);
}
