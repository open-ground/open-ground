package io.github.openground.common.excel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillWrapper;
import io.github.openground.common.excel.ExcelService;
import io.github.openground.common.excel.annotation.ExcelField;
import io.github.openground.common.excel.annotation.ExcelTemplate;
import io.github.openground.common.excel.listener.ImportAnalysisListener;
import io.github.openground.common.excel.model.ImportResult;
import io.github.openground.common.excel.model.ImportRowError;
import io.github.openground.common.excel.resolver.ExcelAnnotationResolver;
import io.github.openground.common.excel.resolver.FieldMeta;
import io.github.openground.common.excel.enums.QueryType;
import io.github.openground.common.excel.spi.DictTranslator;
import io.github.openground.common.excel.spi.ExcelQueryProvider;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel 导入导出核心服务实现
 *
 * @author open-ground
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelServiceImpl implements ExcelService {

    private final ExcelAnnotationResolver annotationResolver;
    private final DictTranslator dictTranslator;
    private final List<ExcelQueryProvider> queryProviders;

    /**
     * URL 编码文件名（JDK 8 的 {@link URLEncoder#encode(String, String)} 声明抛出 UnsupportedEncodingException）
     */
    private String encodeFileName(String raw) {
        try {
            return URLEncoder.encode(raw, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 必然受支持，理论不可达
            throw new IllegalStateException("UTF-8 编码不受支持", e);
        }
    }

    @Override
    public void export(Class<?> voClass, Map<String, Object> params, HttpServletResponse response) {
        ExcelTemplate template = annotationResolver.getTemplate(voClass);
        String fileName = encodeFileName(template.sheetName() + "_" + System.currentTimeMillis());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + fileName + ".xlsx");
        try {
            export(voClass, params, response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Excel 导出失败", e);
        }
    }

    @Override
    public void export(Class<?> voClass, Map<String, Object> params, OutputStream os) {
        List<?> data = queryExportData(voClass, params);
        exportWithData(voClass, data, os);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void exportWithData(Class<?> voClass, List<?> data, OutputStream os) {
        ExcelTemplate template = annotationResolver.getTemplate(voClass);
        List<FieldMeta> fields = annotationResolver.getExportFields(voClass);

        // 构建表头
        List<List<String>> head = fields.stream()
                .map(f -> Collections.singletonList(f.getHeaderName()))
                .collect(Collectors.toList());

        // 构建数据行
        List<List<Object>> rows = new ArrayList<>();
        for (Object item : data) {
            List<Object> row = new ArrayList<>();
            for (FieldMeta field : fields) {
                Object value = ReflectUtil.getFieldValue(item, field.getFieldName());
                // 字典翻译
                if (field.hasDictType() && value != null) {
                    value = dictTranslator.translate(field.getDictType(), String.valueOf(value));
                }
                // 日期格式化
                if (field.hasDateFormat() && value instanceof Date) {
                    value = cn.hutool.core.date.DateUtil.format((Date) value, field.getDateFormat());
                }
                row.add(value);
            }
            rows.add(row);
        }

        // 写入 Excel
        try (ExcelWriter excelWriter = EasyExcel.write(os).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(template.sheetName())
                    .head(head)
                    .build();
            excelWriter.write(rows, writeSheet);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ImportResult importExcel(Class<T> voClass, MultipartFile file, Map<String, Object> params) {
        ExcelTemplate template = annotationResolver.getTemplate(voClass);
        List<FieldMeta> fields = annotationResolver.getImportFields(voClass);

        ImportAnalysisListener<T> listener = new ImportAnalysisListener<>(
                voClass, fields, dictTranslator, annotationResolver);

        try {
            EasyExcel.read(file.getInputStream(), voClass, listener)
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new RuntimeException("Excel 导入读取失败", e);
        }

        ImportResult result = listener.getResult();

        // 校验通过的数据落库
        if (!listener.getValidRows().isEmpty()) {
            if (template.queryType() == io.github.openground.common.excel.enums.QueryType.TABLE) {
                // TABLE 模式：自动 INSERT
                autoInsert(listener.getValidRows(), template.tableName());
            } else {
                // CUSTOM 模式：调用 provider
                for (ExcelQueryProvider provider : queryProviders) {
                    if (provider.getClass().equals(template.queryProvider())
                            || provider.getClass().getName().equals(template.queryProvider().getName())) {
                        provider.handleImport(listener.getValidRows(), params);
                        break;
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void downloadTemplate(Class<?> voClass, HttpServletResponse response) {
        List<FieldMeta> fields = annotationResolver.getImportFields(voClass);
        ExcelTemplate template = annotationResolver.getTemplate(voClass);

        String fileName = encodeFileName(template.sheetName() + "_template");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + fileName + ".xlsx");

        try {
            // 构建表头（仅含导入字段）
            List<List<String>> head = fields.stream()
                    .map(f -> {
                        String name = f.getHeaderName();
                        if (f.isRequired()) {
                            name = "*" + name;
                        }
                        return Collections.singletonList(name);
                    })
                    .collect(Collectors.toList());

            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
                WriteSheet writeSheet = EasyExcel.writerSheet(template.sheetName())
                        .head(head)
                        .build();
                excelWriter.write(new ArrayList<>(), writeSheet);
            }
        } catch (IOException e) {
            throw new RuntimeException("模板下载失败", e);
        }
    }

    /**
     * 导入 Excel 并生成错误文件
     * <p>当导入存在错误行时，返回一个包含原始数据 + "错误原因" 列的 Excel 文件。</p>
     * <p>当所有行都成功时，将 ImportResult 以 JSON 形式写入 response。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public void importAndWriteErrorFile(Class<?> voClass, MultipartFile file,
                                         Map<String, Object> params,
                                         HttpServletResponse response) {
        ImportResult result = importExcel(voClass, file, params);

        if (result.isAllSuccess()) {
            // 全部成功，返回 JSON
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write(com.alibaba.fastjson.JSON.toJSONString(result));
            } catch (IOException e) {
                throw new RuntimeException("写入导入结果失败", e);
            }
            return;
        }

        // 有错误行，生成错误 Excel
        List<FieldMeta> fields = annotationResolver.getImportFields(voClass);
        ExcelTemplate template = annotationResolver.getTemplate(voClass);

        String fileName = encodeFileName(template.sheetName() + "_import_errors");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + fileName + ".xlsx");

        // 表头：原始字段列 + "错误原因"
        List<List<String>> head = new ArrayList<>();
        for (FieldMeta field : fields) {
            head.add(Collections.singletonList(field.getHeaderName()));
        }
        head.add(Collections.singletonList("错误原因"));

        // 数据行：仅包含错误行
        List<List<Object>> rows = new ArrayList<>();
        for (ImportRowError error : result.getErrors()) {
            List<Object> row = new ArrayList<>();
            for (FieldMeta field : fields) {
                Object value = error.getRowData().get(field.getHeaderName());
                row.add(value);
            }
            row.add(error.getMessage()); // 最后一列：错误原因
            rows.add(row);
        }

        try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(template.sheetName() + "_错误")
                    .head(head)
                    .build();
            excelWriter.write(rows, writeSheet);
        } catch (IOException e) {
            throw new RuntimeException("生成错误 Excel 失败", e);
        }
    }

    /**
     * 查询导出数据
     */
    private List<?> queryExportData(Class<?> voClass, Map<String, Object> params) {
        ExcelTemplate template = annotationResolver.getTemplate(voClass);

        QueryType queryType = template.queryType();
        switch (queryType) {
            case TABLE:
                return queryFromTable(voClass, template.tableName(), params);
            case SQL:
                return queryFromSql(template.tableName(), params);
            case CUSTOM:
                return queryFromProvider(template.queryProvider(), params);
            default:
                throw new IllegalArgumentException("不支持的 Excel 查询类型: " + queryType);
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> queryFromTable(Class<?> voClass, String tableName, Map<String, Object> params) {
        // 查找 TableQueryProvider 处理 MyBatis-Plus 查询
        for (ExcelQueryProvider provider : queryProviders) {
            if (provider instanceof io.github.openground.common.excel.provider.TableQueryProvider) {
                return ((io.github.openground.common.excel.provider.TableQueryProvider) provider)
                        .queryByTable(params, tableName);
            }
        }
        log.warn("TableQueryProvider not found, returning empty list for table: {}", tableName);
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<?> queryFromSql(String sql, Map<String, Object> params) {
        for (ExcelQueryProvider provider : queryProviders) {
            if (provider instanceof io.github.openground.common.excel.provider.TableQueryProvider) {
                return ((io.github.openground.common.excel.provider.TableQueryProvider) provider)
                        .queryBySql(sql, params);
            }
        }
        log.warn("TableQueryProvider not found for SQL query");
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<?> queryFromProvider(Class<? extends ExcelQueryProvider> providerClass,
                                       Map<String, Object> params) {
        for (ExcelQueryProvider provider : queryProviders) {
            if (provider.getClass().equals(providerClass)
                    || provider.getClass().getName().equals(providerClass.getName())) {
                return provider.queryExportData(params);
            }
        }
        log.warn("ExcelQueryProvider {} not found in Spring context", providerClass.getName());
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private <T> void autoInsert(List<T> dataList, String tableName) {
        if (dataList == null || dataList.isEmpty()) return;

        // 使用 TableQueryProvider 自动 INSERT
        for (ExcelQueryProvider provider : queryProviders) {
            if (provider instanceof io.github.openground.common.excel.provider.TableQueryProvider) {
                ((io.github.openground.common.excel.provider.TableQueryProvider) provider)
                        .batchInsert(dataList, tableName);
                return;
            }
        }
        log.warn("TableQueryProvider not found, cannot auto-insert into table: {}", tableName);
    }
}
