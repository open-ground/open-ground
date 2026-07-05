package io.github.openground.common.datasource.mapper;

import io.github.openground.common.datasource.entity.SysDatasourceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统数据源 Mapper
 *
 * @author open-ground
 * @since 1.0.2
 */
@Mapper
public interface SysDatasourceMapper {

    int insert(SysDatasourceDO ds);

    int updateById(SysDatasourceDO ds);

    SysDatasourceDO selectById(@Param("id") Long id);

    List<SysDatasourceDO> selectList(SysDatasourceDO query);

    int deleteById(@Param("id") Long id);
}
