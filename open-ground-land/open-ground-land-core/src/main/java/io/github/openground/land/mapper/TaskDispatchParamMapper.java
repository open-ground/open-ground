package io.github.openground.land.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.common.entity.TaskDispatchParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 参数表 Mapper（TASK_DISPATCH_PARAM）
 * <p>继承 BaseMapper 获得通用 CRUD</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskDispatchParamMapper extends BaseMapper<TaskDispatchParam> {

    // ==================== 业务查询（手写 XML） ====================

    List<TaskDispatchParam> selectByPage(Map<String, Object> param);

    TaskDispatchParam selectByPrimaryKey(Map<String, Object> param);

    TaskDispatchParam selectOne(Map<String, Object> param);
}
