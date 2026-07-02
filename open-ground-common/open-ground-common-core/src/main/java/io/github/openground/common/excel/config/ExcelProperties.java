package io.github.openground.common.excel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入导出组件配置属性
 *
 * @author open-ground
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ground.excel")
public class ExcelProperties {

    /** 是否启用 Excel 组件（默认 true） */
    private boolean enabled = true;

    /** 是否启用通用 Controller（默认 true） */
    private boolean controllerEnabled = true;

    /** 额外扫描包路径（默认扫描启动类所在包及其子包） */
    private List<String> scanPackages = new ArrayList<>();
}
