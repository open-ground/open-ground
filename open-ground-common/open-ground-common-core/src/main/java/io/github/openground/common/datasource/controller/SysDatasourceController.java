package io.github.openground.common.datasource.controller;

import com.github.pagehelper.PageInfo;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.datasource.entity.DbTypeVO;
import io.github.openground.common.datasource.entity.SysDatasourceDO;
import io.github.openground.common.datasource.service.SysDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "测试数据源连接")
    @PostMapping("/testConnection")
    public CommonResult<Boolean> testConnection(@RequestBody SysDatasourceDO ds) {
        return CommonResult.success(datasourceService.testConnection(ds));
    }

    @Operation(summary = "查询数据源表列表")
    @GetMapping("/{id}/tables")
    public CommonResult<List<String>> listTables(@PathVariable Long id) {
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
}
