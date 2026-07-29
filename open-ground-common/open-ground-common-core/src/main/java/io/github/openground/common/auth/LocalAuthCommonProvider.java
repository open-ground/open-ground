package io.github.openground.common.auth;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.utils.MapUtil;
import io.github.openground.base.utils.StringUtils;
import io.github.openground.common.jdbc.DynamicJdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AuthCommonProvider 的直连数据库实现（Auth 集成部署模式）
 *
 * <p>通过 {@link DynamicJdbcTemplate} 直接查询 ground-auth 的数据库表，
 * 适用于 {@code ground.mode=auth} 的集成部署模式。</p>
 *
 * <p>所有查询结果统一将 Map key 转为 camelCase 格式（如 user_id → userId），
 * 兼容不同数据库（MySQL 小写、Oracle/达梦 大写）返回的列名大小写差异。</p>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
public class LocalAuthCommonProvider implements AuthCommonProvider {

    private final DynamicJdbcTemplate dynamicJdbcTemplate;
    private final NamedParameterJdbcTemplate primaryJdbcTemplate;

    public LocalAuthCommonProvider(DynamicJdbcTemplate dynamicJdbcTemplate,
                                    NamedParameterJdbcTemplate primaryJdbcTemplate) {
        this.dynamicJdbcTemplate = dynamicJdbcTemplate;
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @Override
    public CommonResult<?> getuserFuncs(String userName) {
        log.debug("直连数据库查询用户功能权限: {}", userName);

        // 1. 查询用户ID
        Map<String, Object> userParams = new HashMap<>();
        userParams.put("username", userName);
        List<Map<String, Object>> userList = primaryJdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user WHERE username = :username", userParams);
        Map<String, Object> user = userList.isEmpty() ? null : userList.get(0);

        if (user == null) {
            return CommonResult.success(new HashMap<String, String>());
        }

        // 统一 key 大小写后取 userId
        Map<String, Object> normalizedUser = normalizeMapKeys(user);
        Object userId = normalizedUser.get("userId");

        // 2. 查询功能权限：角色关联菜单 + 用户直接授权
        Map<String, Object> funcParams = new HashMap<>();
        funcParams.put("userId", userId);
        String sql = "SELECT DISTINCT * FROM ("
                + "SELECT m.menu_name AS FUN_NAME, m.button_id AS FUN_ID "
                + "FROM sys_menu m "
                + "LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id "
                + "LEFT JOIN sys_user_role sur ON rm.role_id = sur.role_id "
                + "WHERE sur.user_id = :userId AND m.menu_type = 3 "
                + "UNION ALL "
                + "SELECT m.menu_name AS FUN_NAME, m.button_id AS FUN_ID "
                + "FROM sys_user_funcs uf "
                + "LEFT JOIN sys_menu m ON uf.func_id = m.menu_id "
                + "WHERE uf.user_id = :userId AND m.menu_type = 3"
                + ") t";

        List<Map<String, Object>> funcList = primaryJdbcTemplate.queryForList(sql, funcParams);

        // 3. 转为 Map<String, String>：FUN_ID → FUN_NAME
        // 统一 key 大小写后取值
        Map<String, String> funcMap = new LinkedHashMap<>();
        for (Map<String, Object> row : funcList) {
            Map<String, Object> normalized = normalizeMapKeys(row);
            Object funId = normalized.get("funId");
            Object funName = normalized.get("funName");
            if (funId != null && funName != null) {
                funcMap.put(funId.toString(), funName.toString());
            }
        }

        return CommonResult.success(funcMap);
    }

    @Override
    public CommonResult<?> getUserInfoByUserName(String userName) {
        log.debug("直连数据库查询用户信息: {}", userName);

        Map<String, Object> params = new HashMap<>();
        params.put("username", userName);

        String sql = "SELECT u.user_id, u.username, u.user_name, u.user_password, "
                + "u.dept_id, u.org_id, u.email, u.mobile, u.status, "
                + "u.sex, u.gmt_create, u.gmt_modified "
                + "FROM sys_user u WHERE u.username = :username";

        List<Map<String, Object>> userList = primaryJdbcTemplate.queryForList(sql, params);
        Map<String, Object> user = userList.isEmpty() ? null : userList.get(0);

        if (user == null) {
            return CommonResult.success(new HashMap<String, Object>());
        }

        // 统一 key 大小为 camelCase
        Map<String, Object> result = normalizeMapKeys(user);

        // 补充 deptName：查询部门表获取部门名称
        Object deptId = result.get("deptId");
        if (deptId != null) {
            Map<String, Object> deptParams = new HashMap<>();
            deptParams.put("deptId", deptId);
            List<Map<String, Object>> deptList = primaryJdbcTemplate.queryForList(
                    "SELECT name FROM sys_dept WHERE dept_id = :deptId", deptParams);
            Map<String, Object> dept = deptList.isEmpty() ? null : deptList.get(0);
            result.put("deptName", dept != null ? normalizeMapKeys(dept).get("name") : null);
        } else {
            result.put("deptName", null);
        }

        // 补充 roleIds：查询用户角色ID列表
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("userId", result.get("userId"));
        List<Map<String, Object>> roleList = primaryJdbcTemplate.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id = :userId", roleParams);
        List<Long> roleIds = new ArrayList<>();
        for (Map<String, Object> roleRow : roleList) {
            Map<String, Object> normalizedRole = normalizeMapKeys(roleRow);
            Object roleId = normalizedRole.get("roleId");
            if (roleId instanceof Number) {
                roleIds.add(((Number) roleId).longValue());
            }
        }
        result.put("roleIds", roleIds);

        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> getUsersByUserNames(List<String> userNames) {
        log.debug("直连数据库批量查询用户信息: {}", userNames);

        if (userNames == null || userNames.isEmpty()) {
            return CommonResult.success(new ArrayList<>());
        }

        // 注意：原逻辑使用 user_name（中文名）而非 username（登录名）
        // 使用 NamedParameterJdbcTemplate 的 IN 子句
        Map<String, Object> params = new HashMap<>();
        params.put("userNames", userNames);

        String sql = "SELECT user_id, user_name "
                + "FROM sys_user WHERE user_name IN (:userNames) "
                + "ORDER BY user_id";

        List<Map<String, Object>> userList = primaryJdbcTemplate.queryForList(sql, params);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : userList) {
            result.add(normalizeMapKeys(row));
        }

        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> getDictByType(String dictType, String appName) {
        log.debug("直连数据库查询字典: dictType={}, appName={}", dictType, appName);

        Map<String, Object> params = new HashMap<>();
        params.put("dictType", dictType);
        // appName 为空时使用空字符串，原逻辑 appName 为空时不做条件过滤
        String appNameVal = appName != null ? appName : "";
        params.put("appName", appNameVal);

        // 查询字典：原逻辑 appName 条件为 (app_name = ? OR app_name = 'all')
        String sql = "SELECT dict_id, dict_name, dict_value, dict_type, description, "
                + "sort_no, parent_id, app_name, remarks, "
                + "config_type, data_source, table_name, filter_col, filter_value, "
                + "value_col, lable_name, sys_module "
                + "FROM sys_dict WHERE dict_type = :dictType "
                + "AND (:appName = '' OR app_name = :appName OR app_name = 'all') "
                + "ORDER BY dict_type, sort_no";

        List<Map<String, Object>> dictList = primaryJdbcTemplate.queryForList(sql, params);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : dictList) {
            Map<String, Object> normalized = normalizeMapKeys(row);
            String configType = (String) normalized.get("configType");

            if (!"1".equals(configType)) {
                // 0 默认配置：直接返回
                result.add(normalized);
            } else {
                // 1 自定义配置：从外部表动态查询
                String dataSource = (String) normalized.get("dataSource");
                String tableName = (String) normalized.get("tableName");
                String filterCol = (String) normalized.get("filterCol");
                String filterValue = (String) normalized.get("filterValue");
                String valueCol = (String) normalized.get("valueCol");
                String lableName = (String) normalized.get("lableName");

                if (tableName != null) {
                    String dynSql = "SELECT * FROM " + tableName;
                    Map<String, Object> dynParams = new HashMap<>();
                    if (filterCol != null && filterValue != null) {
                        dynSql += " WHERE " + filterCol + " = :filterValue";
                        dynParams.put("filterValue", filterValue);
                    }
                    List<Map<String, Object>> dynList;
                    if (dataSource != null && !dataSource.isEmpty()) {
                        dynList = dynamicJdbcTemplate.queryForList(dataSource, dynSql, dynParams);
                    } else {
                        dynList = primaryJdbcTemplate.queryForList(dynSql, dynParams);
                    }

                    for (Map<String, Object> dynRow : dynList) {
                        Map<String, Object> dynNormalized = normalizeMapKeys(dynRow);
                        Map<String, Object> dictDO = new LinkedHashMap<>();
                        dictDO.put("dictType", normalized.get("dictType"));
                        dictDO.put("dictId", normalized.get("dictId"));
                        dictDO.put("description", normalized.get("description"));
                        if (lableName != null && dynNormalized.get(lableName) != null) {
                            dictDO.put("dictName", dynNormalized.get(lableName));
                        }
                        if (valueCol != null && dynNormalized.get(valueCol) != null) {
                            dictDO.put("dictValue", dynNormalized.get(valueCol));
                        }
                        result.add(dictDO);
                    }
                }
            }
        }

        return CommonResult.success(result);
    }

    @Override
    public CommonResult<?> listAllOrg() {
        log.debug("直连数据库查询所有机构");

        String sql = "SELECT org_id, org_name, org_name_id, parent_id, cor_id, dept_id "
                + "FROM sys_org";

        List<Map<String, Object>> orgList = primaryJdbcTemplate.queryForList(sql, new HashMap<>());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : orgList) {
            result.add(normalizeMapKeys(row));
        }

        return CommonResult.success(result);
    }

    /**
     * 统一将 Map 的 key 转为 camelCase 格式
     * <p>兼容不同数据库的列名大小写差异（MySQL 小写、Oracle 大写），
     * 先转小写，再转 camelCase，确保返回结果格式一致。</p>
     *
     * @param raw 原始 Map（key 可能为 USER_ID / user_id 等）
     * @return key 为 camelCase 格式的 Map
     */
    private Map<String, Object> normalizeMapKeys(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        // 先统一转小写
        Map<String, Object> lowerMap = MapUtil.mapKeyLowerCase(raw);
        // 再转 camelCase
        Map<String, Object> result = new LinkedHashMap<>(lowerMap.size());
        for (Map.Entry<String, Object> entry : lowerMap.entrySet()) {
            result.put(StringUtils.convertToHump(entry.getKey()), entry.getValue());
        }
        return result;
    }
}