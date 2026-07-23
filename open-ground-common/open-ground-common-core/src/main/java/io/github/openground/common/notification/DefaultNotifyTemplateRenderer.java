package io.github.openground.common.notification;

import io.github.openground.common.notification.spi.NotifyTemplateRenderer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认通知模板渲染器
 *
 * <p>支持 {@code {{var}}} 占位符替换。
 * <ul>
 *   <li>{@code {{key}}} — 替换为 templateParams 中 key 对应的值</li>
 *   <li>{@code {{key:defaultValue}}} — 如果 key 不存在，使用默认值</li>
 *   <li>{@code {{ key }}} — 允许两侧空格</li>
 * </ul>
 *
 * <p>零外部依赖，纯字符串操作。适用于简单变量替换场景。
 *
 * @author open-ground
 * @since 1.0.6
 */
public class DefaultNotifyTemplateRenderer implements NotifyTemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*(\\w+)\\s*(?::\\s*([^}]*))?\\s*}}");

    @Override
    public String render(String template, Map<String, Object> params) {
        if (template == null) {
            return null;
        }
        if (params == null || params.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            Object value = params.get(key);
            String replacement;
            if (value != null) {
                replacement = value.toString();
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                replacement = "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
