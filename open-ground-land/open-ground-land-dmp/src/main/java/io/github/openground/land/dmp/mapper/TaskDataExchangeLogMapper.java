package io.github.openground.land.dmp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.dmp.entity.TaskDataExchangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据交换执行日志 Mapper
 * <p>继承 BaseMapper 获得通用 CRUD：insert, updateById, selectById 等</p>
 * <p>DMP_DATA_EXCHANGE_LOG 表无 del_flag 字段，不使用逻辑删除</p>
 *
 * @author open-ground
 * @since 1.0.5
 */
@Mapper
public interface TaskDataExchangeLogMapper extends BaseMapper<TaskDataExchangeLog> {

    // ==================== 业务查询（手写 XML） ====================

    /**
     * 按条件查询日志列表（支持 configId/taskType/runStatus 筛选）
     */
    List<TaskDataExchangeLog> selectList(@Param("configId") Long configId,
                                         @Param("taskType") String taskType,
                                         @Param("runStatus") String runStatus);
}
