package io.github.openground.common.datasource.mapper;

import io.github.openground.common.datasource.entity.SysDatasourceTablePermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 数据源表权限 Mapper
 *
 * @author open-ground
 * @since 1.0.4
 */
@Mapper
public interface SysDatasourceTablePermissionMapper {

    /**
     * 批量插入权限记录
     */
    int batchInsert(@Param("list") List<SysDatasourceTablePermissionDO> list);

    /**
     * 根据数据源ID删除所有权限记录
     */
    int deleteByDatasourceId(@Param("datasourceId") Long datasourceId);

    /**
     * 根据数据源ID查询所有权限记录
     */
    List<SysDatasourceTablePermissionDO> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    /**
     * 根据数据源ID和角色ID集合查询可见表名
     */
    List<String> selectAllowedTables(@Param("datasourceId") Long datasourceId,
                                     @Param("roleIds") Set<String> roleIds);

    /**
     * 根据数据源ID和角色ID集合查询可见表名（角色ID为 Long 类型时使用）
     */
    List<String> selectAllowedTablesByLongRoles(@Param("datasourceId") Long datasourceId,
                                                @Param("roleIds") Set<Long> roleIds);

    /**
     * 检查某数据源是否存在权限配置
     */
    int countByDatasourceId(@Param("datasourceId") Long datasourceId);
}
