package io.github.openground.land.dmp.mapper;

import io.github.openground.land.dmp.entity.DmpDataExchangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 数据交换执行日志 Mapper
 *
 * @author open-ground
 * @since 1.0.5
 */
@Mapper
public interface DmpDataExchangeLogMapper {

    int insert(DmpDataExchangeLog log);

    int update(DmpDataExchangeLog log);

    DmpDataExchangeLog selectById(@Param("id") Long id);

    List<DmpDataExchangeLog> selectList(Map<String, Object> param);

    int selectCount(Map<String, Object> param);
}
