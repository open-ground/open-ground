package io.github.openground.common.excel.listener;

import cn.hutool.core.util.ReflectUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import io.github.openground.common.excel.annotation.ExcelField;
import io.github.openground.common.excel.model.ImportResult;
import io.github.openground.common.excel.model.ImportRowError;
import io.github.openground.common.excel.resolver.ExcelAnnotationResolver;
import io.github.openground.common.excel.resolver.FieldMeta;
import io.github.openground.common.excel.spi.DictTranslator;
import io.github.openground.common.excel.spi.ExcelDataValidator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入分析监听器
 *
 * <p>逐行读取 Excel 数据，执行必填校验、字典翻译、自定义校验。</p>
 *
 * @param <T> VO 类型
 * @author open-ground
 */
@Slf4j
public class ImportAnalysisListener<T> extends AnalysisEventListener<T> {

    private final Class<T> voClass;
    private final List<FieldMeta> fields;
    private final DictTranslator dictTranslator;
    private final ExcelAnnotationResolver annotationResolver;

    @Getter
    private final ImportResult result = new ImportResult();

    @Getter
    private final List<T> validRows = new ArrayList<>();

    /** 表头行映射：Excel 列头 → fieldName */
    private java.util.Map<String, String> headerMapping;

    /** 当前行号（从 1 开始，表头算第 1 行） */
    private int currentRowNum = 1;

    public ImportAnalysisListener(Class<T> voClass, List<FieldMeta> fields,
                                   DictTranslator dictTranslator,
                                   ExcelAnnotationResolver annotationResolver) {
        this.voClass = voClass;
        this.fields = fields;
        this.dictTranslator = dictTranslator;
        this.annotationResolver = annotationResolver;
    }

    @Override
    public void invokeHeadMap(java.util.Map<Integer, String> headMap, AnalysisContext context) {
        // 构建表头映射：列索引 → 字段名
        headerMapping = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, String> entry : headMap.entrySet()) {
            String headerName = entry.getValue();
            // 去除表头中的 "*" 标记
            if (headerName.startsWith("*")) {
                headerName = headerName.substring(1);
            }
            for (FieldMeta field : fields) {
                if (field.getHeaderName().equals(headerName)) {
                    headerMapping.put(String.valueOf(entry.getKey()), field.getFieldName());
                    break;
                }
            }
        }
        currentRowNum = 1;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        currentRowNum++;
        List<String> rowErrors = new ArrayList<>();

        // 将 Map 类型的数据转为 VO（EasyExcel 可能返回 Map）
        T vo = convertToVo(data);
        if (vo == null) {
            result.addError(new ImportRowError(currentRowNum, "数据转换失败"));
            return;
        }

        // 逐字段校验
        for (FieldMeta field : fields) {
            Object value = ReflectUtil.getFieldValue(vo, field.getFieldName());

            // 必填校验
            if (field.isRequired() && (value == null || value.toString().trim().isEmpty())) {
                rowErrors.add(field.getHeaderName() + " 为必填项");
                continue;
            }
            if (value == null) continue;

            // 字典反向翻译（label → code）
            if (field.hasDictType()) {
                String translated = dictTranslator.translate(field.getDictType(), value.toString());
                if (!translated.equals(value.toString())) {
                    ReflectUtil.setFieldValue(vo, field.getFieldName(), translated);
                }
            }

            // 日期解析
            if (field.hasDateFormat() && value instanceof String) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(field.getDateFormat());
                    java.util.Date date = sdf.parse((String) value);
                    ReflectUtil.setFieldValue(vo, field.getFieldName(), date);
                } catch (Exception e) {
                    rowErrors.add(field.getHeaderName() + " 日期格式不正确，应为 " + field.getDateFormat());
                }
            }

            // 自定义校验器
            if (!field.getValidatorClass().equals(
                    io.github.openground.common.excel.annotation.ExcelField.VoidValidator.class.getName())) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<ExcelDataValidator> vClass =
                            (Class<ExcelDataValidator>) Class.forName(field.getValidatorClass());
                    ExcelDataValidator validator = vClass.getDeclaredConstructor().newInstance();
                    String error = validator.validate(value, field.getHeaderName());
                    if (error != null) {
                        rowErrors.add(error);
                    }
                } catch (Exception e) {
                    log.warn("无法实例化校验器 {}: {}", field.getValidatorClass(), e.getMessage());
                }
            }
        }

        if (rowErrors.isEmpty()) {
            validRows.add(vo);
            result.incrementSuccess();
        } else {
            // 捕获错误行的原始数据（用于生成错误 Excel）
            java.util.Map<String, Object> rowData = new java.util.LinkedHashMap<>();
            for (FieldMeta field : fields) {
                Object value = ReflectUtil.getFieldValue(vo, field.getFieldName());
                rowData.put(field.getHeaderName(), value);
            }
            result.addError(new ImportRowError(currentRowNum,
                    String.join("; ", rowErrors), rowData));
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        result.setTotalRows(validRows.size() + result.getErrors().size());
        log.info("Excel 导入完成：总行数={}, 成功={}, 失败={}",
                result.getTotalRows(), result.getSuccessRows(), result.getFailRows());
    }

    @SuppressWarnings("unchecked")
    private T convertToVo(Object data) {
        if (voClass.isInstance(data)) {
            return (T) data;
        }
        // 如果是 Map，转为 VO
        if (data instanceof java.util.Map) {
            try {
                T vo = voClass.getDeclaredConstructor().newInstance();
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) data;
                for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                    String fieldName = headerMapping != null
                            ? headerMapping.getOrDefault(entry.getKey(), entry.getKey())
                            : entry.getKey();
                    Field field = ReflectUtil.getField(voClass, fieldName);
                    if (field != null) {
                        ReflectUtil.setFieldValue(vo, fieldName, entry.getValue());
                    }
                }
                return vo;
            } catch (Exception e) {
                log.error("Map 转 VO 失败", e);
                return null;
            }
        }
        return null;
    }
}
