package io.github.openground.common.notification.spi;

import java.util.Map;

/**
 * 通知模板渲染器 SPI
 *
 * <p>负责将含 {@code {{var}}} 占位符的模板字符串渲染为最终文本。
 * 组件提供默认实现（零依赖的字符串替换），业务方可通过 SPI 替换为自己的模板引擎（如 FreeMarker）。
 *
 * @author open-ground
 * @since 1.0.6
 */
public interface NotifyTemplateRenderer {

    /**
     * 渲染模板
     *
     * @param template 含占位符的模板字符串，如 "您好，{{userName}}"
     * @param params   参数表
     * @return 渲染后的文本；template 为 null 时返回 null
     */
    String render(String template, Map<String, Object> params);
}
