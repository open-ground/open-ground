package io.github.openground.land.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.openground.land.common.entity.TaskEtlTableConf;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * ETL 入库配置表 Mapper（TASK_ETL_TABLE_CONF）
 * <p>继承 BaseMapper 获得通用 CRUD</p>
 *
 * @author jack.zhang
 * @since 2026-06-24
 */
@Mapper
public interface TaskEtlTableConfMapper extends BaseMapper<TaskEtlTableConf> {

    List<Map<String, Object>> segmentEtlTable(Map<String, Object> param);

    List<TaskEtlTableConf> getAllConfig();
}
