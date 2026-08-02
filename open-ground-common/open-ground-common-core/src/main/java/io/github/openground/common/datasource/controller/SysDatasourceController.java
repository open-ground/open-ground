package io.github.openground.common.datasource.controller;

import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.datasource.dto.RoleInfo;
import io.github.openground.common.datasource.entity.DbTypeVO;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.entity.SysDatasourceTablePermissionDO;
import io.github.openground.common.datasource.service.SysDatasourceService;
import io.github.openground.common.datasource.service.SysDatasourceTablePermissionService;
import io.github.openground.common.datasource.spi.RoleProvider;
import io.github.openground.common.jdbc.DynamicDataSourceManager;
import io.github.openground.common.security.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统数据源管理 Controller
 *
 * @author open-ground
 * @since 1.0.2
 */
@Slf4j
@Tag(name = "系统数据源管理")
@RestController
@RequestMapping("/sys/datasource")
public class SysDatasourceController {

    @Autowired
    private SysDatasourceService datasourceService;

    @Autowired(required = false)
    private DynamicDataSourceManager dynamicDataSourceManager;

    @Autowired(required = false)
    private SysDatasourceTablePermissionService tablePermissionService;

    @Autowired(required = false)
    private RoleProvider roleProvider;

    @Operation(summary = "创建数据源")
    @PostMapping("/create")
    public CommonResult<SysDatasourceDO> create(@Valid @RequestBody SysDatasourceDO ds) {
        return CommonResult.success(datasourceService.create(ds));
    }

    @Operation(summary = "更新数据源")
    @PostMapping("/update")
    public CommonResult<SysDatasourceDO> update(@Valid @RequestBody SysDatasourceDO ds) {
        return CommonResult.success(datasourceService.update(ds));
    }

    @Operation(summary = "删除数据源")
    @PostMapping("/delete/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public CommonResult<SysDatasourceDO> getById(@PathVariable Long id) {
        return CommonResult.success(datasourceService.getById(id));
    }

    @Operation(summary = "分页查询数据源列表")
    @PostMapping("/list")
    public CommonResult<PageInfo<SysDatasourceDO>> list(@RequestBody SysDatasourceDO query) {
        return CommonResult.success(datasourceService.list(query));
    }

    @Operation(summary = "查询数据源列表")
    @PostMapping("/listAll")
    public CommonResult<List<SysDatasourceDO>> listAll(@RequestBody SysDatasourceDO query) {
        return CommonResult.success(datasourceService.listAllNoPasswd(query));
    }

    @Operation(summary = "Provider查询数据源列表")
    @PostMapping("/listAllForProvider")
    public CommonResult<List<SysDatasourceDO>> listAllDatasourceProvider() {
        return CommonResult.success(datasourceService.listAllForProvider());
    }

    @Operation(summary = "测试数据源连接")
    @PostMapping("/testConnection")
    public CommonResult<Void> testConnection(
            @RequestBody SysDatasourceDO ds,
            @RequestParam(defaultValue = "true") boolean passwordEncrypted) {
        try {
            boolean connected = datasourceService.testConnection(ds, passwordEncrypted);
            if (connected) {
                return CommonResult.success();
            }
            return CommonResult.error("518006", "数据源连接测试失败：连接无效");
        } catch (Exception e) {
            return CommonResult.error("518006", "数据源连接测试失败：" + e.getMessage());
        }
    }

    @Operation(summary = "查询数据源表列表")
    @GetMapping("/{id}/tables")
    public CommonResult<List<Map<String, Object>>> listTables(@PathVariable Long id) {
        return CommonResult.success(datasourceService.listTables(id));
    }

    @Operation(summary = "查询表字段列表")
    @GetMapping("/{id}/tables/{tableName}/columns")
    public CommonResult<List<Map<String, Object>>> listTableColumns(
            @PathVariable Long id, @PathVariable String tableName) {
        return CommonResult.success(datasourceService.listTableColumns(id, tableName));
    }

    @Operation(summary = "获取所有支持的数据库类型列表")
    @GetMapping("/db-types")
    public CommonResult<List<DbTypeVO>> listDbTypes() {
        return CommonResult.success(DbTypeVO.allTypes());
    }

    /**
     * 刷新数据源缓存
     *
     * <p>重新从所有 Provider（含 sys_datasource 表）加载数据源描述符到缓存，
     * 并关闭不再存在的连接池。修改数据源后调用此接口生效。
     *
     * @return 刷新结果
     */
    @Operation(summary = "刷新数据源缓存")
    @PostMapping("/refresh")
    public CommonResult<Map<String, Object>> refresh() {
        if (dynamicDataSourceManager == null) {
            return CommonResult.error("500", "动态数据源管理器未启用");
        }
        dynamicDataSourceManager.refresh();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceCount", dynamicDataSourceManager.getDataSourceList().size());
        result.put("datasourceNames", dynamicDataSourceManager.getDataSourceNames());
        return CommonResult.success(result);
    }

    // ==================== 表权限管理 ====================

    @Operation(summary = "获取全部角色列表（通过 RoleProvider SPI）")
    @GetMapping("/roles")
    public CommonResult<List<RoleInfo>> listRoles() {
        if (roleProvider == null) {
            return CommonResult.error("500", "角色服务未启用，未配置 RoleProvider 实现");
        }
        return CommonResult.success(roleProvider.getAllRoles());
    }

    @Operation(summary = "查询数据源表权限")
    @GetMapping("/{id}/alltables")
    public CommonResult<Map<String, Object>> alltables(@PathVariable Long id) {
        // 获取全部表名（跳过权限过滤），含表名+注释
        List<Map<String, Object>> allTables = datasourceService.listAllTables(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allTables", allTables);

        // 权限数据在表权限服务可用时返回，否则返回空 Map
        if (tablePermissionService != null) {
            List<SysDatasourceTablePermissionDO> permissions = tablePermissionService.queryByDatasource(id);
            result.put("roleTableMap", tablePermissionService.groupByRole(permissions));
        } else {
            result.put("roleTableMap", Collections.emptyMap());
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "保存数据源表权限")
    @PostMapping("/{id}/table-permission/save")
    public CommonResult<Void> saveTablePermission(
            @PathVariable Long id,
            @RequestBody Map<String, List<String>> roleTableMap) {
        if (tablePermissionService == null) {
            return CommonResult.error("500", "表权限服务未启用");
        }

        String currentUser = SecurityContextHolder.getCurrentUsername();
        tablePermissionService.saveBatch(id, roleTableMap, currentUser);
        log.info("table permission saved for datasource {}, by user: {}", id, currentUser);
        return CommonResult.success();
    }
}
