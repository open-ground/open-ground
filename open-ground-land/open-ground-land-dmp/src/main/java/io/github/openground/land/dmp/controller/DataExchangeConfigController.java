package io.github.openground.land.dmp.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.land.api.dto.DataExchangeConfigDTO;
import io.github.openground.land.dmp.service.DataExchangeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据交换配置控制器
 *
 * @author jack.zhang
 * @since 2026-07-17
 */
@Slf4j
@Tag(name = "数据交换配置")
@RestController
@RequestMapping("/dmp/exchange/config")
public class DataExchangeConfigController {

    @Autowired
    private DataExchangeConfigService configService;

    @Operation(summary = "配置列表查询")
    @PostMapping("/list")
    public CommonResult<?> list(@RequestBody DataExchangeConfigDTO request) {
        return configService.list(request);
    }

    @Operation(summary = "配置详情")
    @PostMapping("/get")
    public CommonResult<?> get(@RequestBody DataExchangeConfigDTO request) {
        return configService.getById(request.getId());
    }

    @Operation(summary = "保存配置（新增/更新）")
    @PostMapping("/save")
    public CommonResult<?> save(@Valid @RequestBody DataExchangeConfigDTO request) {
        return configService.save(request);
    }

    @Operation(summary = "删除配置")
    @PostMapping("/delete")
    public CommonResult<?> delete(@RequestBody DataExchangeConfigDTO request) {
        return configService.delete(request.getId());
    }
}
