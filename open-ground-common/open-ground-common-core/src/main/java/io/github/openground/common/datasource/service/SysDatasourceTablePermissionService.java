package io.github.openground.common.datasource.service;

import io.github.openground.common.datasource.entity.SysDatasourceTablePermissionDO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据源表权限 Service 接口
 *
 * @author open-ground
 * @since 1.0.4
 */
public interface SysDatasourceTablePermissionService {

    /**
     * 查询数据源的全部权限记录
     */
    List<SysDatasourceTablePermissionDO> queryByDatasource(Long datasourceId);

    /**
     * 查询数据源在指定角色集合下可见的表名
     *
     * @param datasourceId 数据源ID
     * @param roleIds      角色ID集合（String 类型）
     * @return 可见表名集合；若数据源无权限配置则返回 {@code null}
     */
    Set<String> queryAllowedTables(Long datasourceId, Set<String> roleIds);

    /**
     * 查询数据源在指定角色集合下可见的表名（角色ID为 Long 类型时使用）
     *
     * @param datasourceId 数据源ID
     * @param roleIds      角色ID集合（Long 类型）
     * @return 可见表名集合；若数据源无权限配置则返回 {@code null}
     */
    Set<String> queryAllowedTablesByLongRoles(Long datasourceId, Set<Long> roleIds);

    /**
     * 保存数据源表权限（先删后插，事务内完成）
     *
     * @param datasourceId 数据源ID
     * @param roleTableMap 角色→可见表名列表的映射
     * @param createBy     操作人
     */
    void saveBatch(Long datasourceId, Map<String, List<String>> roleTableMap, String createBy);

    /**
     * 检查数据源是否存在权限配置
     */
    boolean hasPermissionConfig(Long datasourceId);

    /**
     * 将权限记录按角色分组
     *
     * @param permissions 权限记录列表
     * @return 角色→表名列表的映射
     */
    Map<String, List<String>> groupByRole(List<SysDatasourceTablePermissionDO> permissions);
}
