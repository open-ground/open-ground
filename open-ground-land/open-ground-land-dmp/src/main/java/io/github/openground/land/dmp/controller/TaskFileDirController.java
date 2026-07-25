package io.github.openground.land.dmp.controller;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.log.annotation.OptLog;
import io.github.openground.common.log.enums.OptType;
import io.github.openground.land.dmp.entity.TaskFileDir;
import io.github.openground.land.dmp.service.TaskFileDirService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件目录管理控制器
 *
 * @author jack.zhang
 * @since 1.0.7
 */
@Slf4j
@Tag(name = "文件目录管理")
@RestController
@RequestMapping("/task/exchange/filedir")
public class TaskFileDirController {

    @Autowired
    private TaskFileDirService fileDirService;

    @Operation(summary = "目录列表查询（分页）")
    @OptLog(optType = OptType.OTHER, optRemark = "文件目录列表查询")
    @PostMapping("/list")
    public CommonResult<?> list(@RequestBody TaskFileDir request) {
        String dirName = request.getDirName();
        String dirType = request.getDirType();
        int pageIndex = 1;
        int pageSize = 10;
        return fileDirService.list(dirName, dirType, pageIndex, pageSize);
    }

    @Operation(summary = "查询全部目录（下拉选择用）")
    @OptLog(optType = OptType.OTHER, optRemark = "查询全部文件目录")
    @PostMapping("/listAll")
    public CommonResult<?> listAll() {
        return fileDirService.listAll();
    }

    @Operation(summary = "目录详情")
    @OptLog(optType = OptType.OTHER, optRemark = "文件目录详情")
    @PostMapping("/get")
    public CommonResult<?> get(@RequestBody TaskFileDir request) {
        return fileDirService.getById(request.getId());
    }

    @Operation(summary = "保存目录（新增/更新）")
    @OptLog(optType = OptType.INSERT, optRemark = "保存文件目录")
    @PostMapping("/save")
    public CommonResult<?> save(@RequestBody TaskFileDir request) {
        return fileDirService.save(request);
    }

    @Operation(summary = "删除目录")
    @OptLog(optType = OptType.DELETE, optRemark = "删除文件目录")
    @PostMapping("/delete")
    public CommonResult<?> delete(@RequestBody TaskFileDir request) {
        return fileDirService.delete(request.getId());
    }
}
