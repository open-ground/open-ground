package io.github.openground.land.dmp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.dmp.entity.TaskDataExchangeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据交换配置 Mapper
 * <p>继承 BaseMapper 获得通用 CRUD：insert, deleteById, updateById, selectById, selectList 等</p>
 * <p>逻辑删除由 @TableLogic 自动处理，selectById/updateById 自动带 del_flag='0' 条件，deleteById 自动置 del_flag='1'</p>
 *
 * @author jack.zhang
 * @since 1.0.6
 */
@Mapper
public interface TaskDataExchangeConfigMapper extends BaseMapper<TaskDataExchangeConfig> {

    // ==================== 业务查询（手写 XML） ====================

    /**
     * 按条件查询配置列表（支持 taskName 模糊、taskType/taskStatus 精确、dsId 关联查询）
     */
    List<TaskDataExchangeConfig> selectList(@Param("taskName") String taskName,
                                            @Param("taskType") String taskType,
                                            @Param("taskStatus") String taskStatus,
                                            @Param("dsId") Long dsId);

    /**
     * 查询所有启用的配置（血缘图用）
     */
    List<TaskDataExchangeConfig> selectEnabledConfigs();
}
