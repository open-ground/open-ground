package io.github.openground.common.excel.config;

import cn.hutool.core.util.StrUtil;
import io.github.openground.common.excel.annotation.ExcelTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Excel 实体扫描器
 * <p>启动时扫描 classpath 下所有标注 {@link ExcelTemplate} 的类，建立名称到类的映射。</p>
 *
 * <p>默认扫描启动类所在包及其子包，也支持通过 {@link ExcelProperties#getScanPackages()} 添加额外扫描包。</p>
 *
 * @author open-ground
 */
@Slf4j
@Component
public class ExcelEntityScanner implements ApplicationListener<ApplicationReadyEvent> {

    /** entityName → VO Class */
    @Getter
    private final Map<String, Class<?>> entityMap = new HashMap<>();

    private final ExcelProperties excelProperties;

    public ExcelEntityScanner(ExcelProperties excelProperties) {
        this.excelProperties = excelProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        scanEntities(event);
    }

    private void scanEntities(ApplicationReadyEvent event) {
        // 先扫已配置的扫描包
        if (excelProperties.getScanPackages() != null) {
            for (String pkg : excelProperties.getScanPackages()) {
                scanPackage(pkg);
            }
        }

        // 再扫描启动类所在包（如果 mainApplicationClass 存在）
        try {
            Object mainApp = event.getSpringApplication().getMainApplicationClass();
            if (mainApp instanceof Class) {
                String mainPackage = ((Class<?>) mainApp).getPackageName();
                scanPackage(mainPackage);
            }
        } catch (Exception e) {
            log.debug("无法获取启动类包路径: {}", e.getMessage());
        }

        log.info("Excel 实体扫描完成，共发现 {} 个 @ExcelTemplate 实体", entityMap.size());
        entityMap.forEach((name, clazz) ->
                log.debug("  → {} : {}", name, clazz.getName()));
    }

    private void scanPackage(String basePackage) {
        if (StrUtil.isBlank(basePackage)) return;

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ExcelTemplate.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        for (BeanDefinition bd : candidates) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                ExcelTemplate template = clazz.getAnnotation(ExcelTemplate.class);
                if (template == null) continue;

                String entityName = template.name();
                if (StrUtil.isBlank(entityName)) {
                    // 自动生成：UserExcelVO → user-excel-vo
                    entityName = StrUtil.toSymbolCase(
                            clazz.getSimpleName().replace("ExcelVO", "")
                                    .replace("VO", ""), '-');
                    if (entityName.startsWith("-")) {
                        entityName = entityName.substring(1);
                    }
                    if (StrUtil.isBlank(entityName)) {
                        entityName = StrUtil.toSymbolCase(clazz.getSimpleName(), '-');
                    }
                }

                String finalEntityName = entityName.toLowerCase();
                if (entityMap.containsKey(finalEntityName)) {
                    log.warn("Excel 实体名称冲突: {} → {} 和 {}",
                            finalEntityName, entityMap.get(finalEntityName).getName(), clazz.getName());
                }
                entityMap.put(finalEntityName, clazz);
            } catch (ClassNotFoundException e) {
                log.warn("无法加载 Excel 实体类: {}", bd.getBeanClassName());
            }
        }
    }
}
