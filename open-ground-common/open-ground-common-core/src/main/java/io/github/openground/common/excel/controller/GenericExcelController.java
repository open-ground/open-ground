package io.github.openground.common.excel.controller;

import io.github.openground.common.excel.ExcelService;
import io.github.openground.common.excel.config.ExcelEntityScanner;
import io.github.openground.common.excel.model.ImportResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 通用 Excel 导入导出控制器
 *
 * <p>内置 REST 端点，无需业务模块编写 Controller：</p>
 * <ul>
 *   <li>POST /ground/excel/{entityName}/export - 导出 Excel</li>
 *   <li>POST /ground/excel/{entityName}/import - 导入 Excel</li>
 *   <li>GET  /ground/excel/{entityName}/template - 下载导入模板</li>
 * </ul>
 *
 * <p>{@code entityName} 由 {@link ExcelEntityScanner} 自动扫描注册：</p>
 * <ul>
 *   <li>@ExcelTemplate.name() 指定名称</li>
 *   <li>或自动取类名转 kebab-case（如 UserExcelVO → user-excel）</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ground/excel")
@ConditionalOnProperty(prefix = "ground.excel", name = "controller-enabled", havingValue = "true", matchIfMissing = true)
public class GenericExcelController {

    private final ExcelService excelService;
    private final ExcelEntityScanner excelEntityScanner;

    /**
     * 导出 Excel
     *
     * @param entityName 实体名称
     * @param params     查询参数（JSON body，可选）
     * @param response   HTTP 响应
     */
    @PostMapping("/{entityName}/export")
    public void export(@PathVariable String entityName,
                       @RequestBody(required = false) Map<String, Object> params,
                       HttpServletResponse response) {
        Class<?> voClass = getEntityClass(entityName);
        excelService.export(voClass, params, response);
    }

    /**
     * 导入 Excel
     *
     * @param entityName 实体名称
     * @param file       上传的 Excel 文件
     * @param params     导入参数（可选）
     * @param errorAsFile 是否以 Excel 文件形式返回错误详情（默认 false）
     * @param response   HTTP 响应（errorAsFile=true 时使用）
     * @return 导入结果（errorAsFile=false 时返回 JSON）
     */
    @PostMapping("/{entityName}/import")
    public Object importExcel(@PathVariable String entityName,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Map<String, Object> params,
                               @RequestParam(defaultValue = "false") boolean errorAsFile,
                               HttpServletResponse response) {
        Class<?> voClass = getEntityClass(entityName);
        if (errorAsFile) {
            excelService.importAndWriteErrorFile(voClass, file, params, response);
            return null;
        }
        return excelService.importExcel(voClass, file, params);
    }

    /**
     * 下载导入模板（仅表头）
     *
     * @param entityName 实体名称
     * @param response   HTTP 响应
     */
    @GetMapping("/{entityName}/template")
    public void downloadTemplate(@PathVariable String entityName,
                                  HttpServletResponse response) {
        Class<?> voClass = getEntityClass(entityName);
        excelService.downloadTemplate(voClass, response);
    }

    /**
     * 获取已注册的实体列表（辅助接口）
     */
    @GetMapping("/entities")
    public Map<String, String> listEntities() {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        excelEntityScanner.getEntityMap().forEach((name, clazz) ->
                result.put(name, clazz.getName()));
        return result;
    }

    private Class<?> getEntityClass(String entityName) {
        Class<?> voClass = excelEntityScanner.getEntityMap().get(entityName.toLowerCase());
        if (voClass == null) {
            throw new IllegalArgumentException(
                    "未找到 Excel 实体: " + entityName + "，可用实体: " +
                            excelEntityScanner.getEntityMap().keySet());
        }
        return voClass;
    }
}
