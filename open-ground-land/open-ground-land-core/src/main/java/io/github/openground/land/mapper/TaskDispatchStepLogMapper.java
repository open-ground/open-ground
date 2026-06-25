package io.github.openground.land.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.common.entity.TaskDispatchStepLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 分段执行记录表 Mapper（TASK_DISPATCH_STEP_LOG）
 * <p>继承 BaseMapper 获得通用 CRUD</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskDispatchStepLogMapper extends BaseMapper<TaskDispatchStepLog> {

    TaskDispatchStepLog selectOne(Map<String, Object> param);

    List<TaskDispatchStepLog> selectList(Map<String, Object> param);
}
