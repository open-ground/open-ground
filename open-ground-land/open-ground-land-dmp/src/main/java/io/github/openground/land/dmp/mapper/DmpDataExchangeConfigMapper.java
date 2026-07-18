package io.github.openground.land.dmp.mapper;

import io.github.openground.land.dmp.entity.DmpDataExchangeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 数据交换配置 Mapper
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Mapper
public interface DmpDataExchangeConfigMapper {

    int insert(DmpDataExchangeConfig config);

    int update(DmpDataExchangeConfig config);

    int deleteById(@Param("id") Long id);

    DmpDataExchangeConfig selectById(@Param("id") Long id);

    List<DmpDataExchangeConfig> selectList(Map<String, Object> param);

    int selectCount(Map<String, Object> param);
}
