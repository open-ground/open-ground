package io.github.openground.common.datasource.service.impl;

import io.github.openground.common.datasource.entity.SysDatasourceTablePermissionDO;
import io.github.openground.common.datasource.mapper.SysDatasourceTablePermissionMapper;
import io.github.openground.common.datasource.service.SysDatasourceTablePermissionService;
import io.github.openground.common.keygen.KeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源表权限 Service 实现
 *
 * @author open-ground
 * @since 1.0.4
 */
@Service
public class SysDatasourceTablePermissionServiceImpl implements SysDatasourceTablePermissionService {

    @Autowired
    private SysDatasourceTablePermissionMapper tablePermissionMapper;

    @Override
    public List<SysDatasourceTablePermissionDO> queryByDatasource(Long datasourceId) {
        return tablePermissionMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public Set<String> queryAllowedTables(Long datasourceId, Set<String> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptySet();
        }
        // 先检查是否存在权限配置
        if (tablePermissionMapper.countByDatasourceId(datasourceId) == 0) {
            return null; // 无配置 → null 表示未配置权限
        }
        List<String> tables = tablePermissionMapper.selectAllowedTables(datasourceId, roleIds);
        return new HashSet<>(tables);
    }

    @Override
    public Set<String> queryAllowedTablesByLongRoles(Long datasourceId, Set<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptySet();
        }
        if (tablePermissionMapper.countByDatasourceId(datasourceId) == 0) {
            return null;
        }
        List<String> tables = tablePermissionMapper.selectAllowedTablesByLongRoles(datasourceId, roleIds);
        return new HashSet<>(tables);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(Long datasourceId, Map<String, List<String>> roleTableMap, String createBy) {
        // 先删后插
        tablePermissionMapper.deleteByDatasourceId(datasourceId);

        if (roleTableMap == null || roleTableMap.isEmpty()) {
            return;
        }

        Date now = new Date();
        List<SysDatasourceTablePermissionDO> list = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : roleTableMap.entrySet()) {
            String roleId = entry.getKey();
            List<String> tables = entry.getValue();
            if (tables == null || tables.isEmpty()) {
                continue;
            }
            for (String tableName : tables) {
                SysDatasourceTablePermissionDO permission = new SysDatasourceTablePermissionDO();
                permission.setId(KeyGenerator.getInternalKey());
                permission.setDatasourceId(datasourceId);
                permission.setRoleId(roleId);
                permission.setTableName(tableName);
                permission.setCreateBy(createBy);
                permission.setCreateTime(now);
                permission.setUpdateBy(createBy);
                permission.setUpdateTime(now);
                list.add(permission);
            }
        }

        if (!list.isEmpty()) {
            tablePermissionMapper.batchInsert(list);
        }
    }

    @Override
    public boolean hasPermissionConfig(Long datasourceId) {
        return tablePermissionMapper.countByDatasourceId(datasourceId) > 0;
    }

    @Override
    public Map<String, List<String>> groupByRole(List<SysDatasourceTablePermissionDO> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyMap();
        }
        return permissions.stream()
                .collect(Collectors.groupingBy(
                        SysDatasourceTablePermissionDO::getRoleId,
                        Collectors.mapping(SysDatasourceTablePermissionDO::getTableName, Collectors.toList())
                ));
    }
}
