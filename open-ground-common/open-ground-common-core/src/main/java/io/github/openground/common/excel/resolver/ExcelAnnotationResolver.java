package io.github.openground.common.excel.resolver;

import cn.hutool.core.util.ReflectUtil;
import io.github.openground.common.excel.annotation.ExcelField;
import io.github.openground.common.excel.annotation.ExcelTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel 注解解析器
 * <p>解析 {@link ExcelTemplate} 和 {@link ExcelField} 注解，缓存元数据。</p>
 *
 * @author open-ground
 */
@Component
public class ExcelAnnotationResolver {

    /** 缓存：VO 类 → @ExcelTemplate */
    private final Map<Class<?>, ExcelTemplate> templateCache = new HashMap<>();

    /** 缓存：VO 类 → 字段元数据列表 */
    private final Map<Class<?>, List<FieldMeta>> fieldCache = new HashMap<>();

    /**
     * 获取 VO 类的 @ExcelTemplate 注解
     */
    public ExcelTemplate getTemplate(Class<?> voClass) {
        return templateCache.computeIfAbsent(voClass, clazz -> {
            ExcelTemplate template = clazz.getAnnotation(ExcelTemplate.class);
            if (template == null) {
                throw new IllegalArgumentException(
                        "Class " + clazz.getName() + " is not annotated with @ExcelTemplate");
            }
            return template;
        });
    }

    /**
     * 获取导出的字段列表（按 order 排序，过滤 exportIgnore）
     */
    public List<FieldMeta> getExportFields(Class<?> voClass) {
        return getFields(voClass).stream()
                .filter(f -> !f.isExportIgnore())
                .sorted(Comparator.comparingInt(FieldMeta::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 获取导入的字段列表（按 order 排序，过滤 importIgnore）
     */
    public List<FieldMeta> getImportFields(Class<?> voClass) {
        return getFields(voClass).stream()
                .filter(f -> !f.isImportIgnore())
                .sorted(Comparator.comparingInt(FieldMeta::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 获取 VO 类所有字段元数据
     */
    public List<FieldMeta> getFields(Class<?> voClass) {
        return fieldCache.computeIfAbsent(voClass, clazz -> {
            Field[] fields = ReflectUtil.getFields(clazz);
            List<FieldMeta> list = new ArrayList<>();
            for (Field field : fields) {
                ExcelField ef = field.getAnnotation(ExcelField.class);
                if (ef == null) continue;

                FieldMeta meta = FieldMeta.builder()
                        .fieldName(field.getName())
                        .headerName(ef.headerName())
                        .order(ef.order())
                        .dictType(ef.dictType())
                        .dateFormat(ef.dateFormat())
                        .required(ef.required())
                        .validatorClass(ef.validator().getName())
                        .exportIgnore(ef.exportIgnore())
                        .importIgnore(ef.importIgnore())
                        .fieldType(field.getType())
                        .build();
                list.add(meta);
            }
            return list;
        });
    }

    /**
     * 根据 headerName 查找字段元数据
     */
    public Optional<FieldMeta> getFieldByHeader(Class<?> voClass, String headerName) {
        return getFields(voClass).stream()
                .filter(f -> f.getHeaderName().equals(headerName))
                .findFirst();
    }
}
