package io.github.openground.land.dmp.executor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件路径日期变量解析器
 *
 * <p>在执行时替换路径/文件名中的日期占位符为实际日期，支持：
 * <ul>
 *   <li>{@code ${yyyyMMdd}} — 当天 8 位日期，如 20260718</li>
 *   <li>{@code ${yyyy-MM-dd}} — 当天带横线，如 2026-07-18</li>
 *   <li>{@code ${yyyyMM}} — 年月，如 202607</li>
 *   <li>{@code ${yyyy}} — 年，如 2026</li>
 *   <li>{@code ${MMdd}} — 月日，如 0718</li>
 *   <li>{@code ${HHmmss}} — 时分秒，如 143522</li>
 * </ul>
 *
 * <p>示例：{@code /data/files/report_${yyyyMMdd}.csv}
 * → {@code /data/files/report_20260718.csv}
 *
 * @author open-ground
 * @since 1.0.5
 */
public class DatePathResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 解析路径中的日期占位符为当前日期的实际值
     *
     * @param pathTemplate 含占位符的路径模板
     * @return 渲染后的实际路径
     */
    public static String resolve(String pathTemplate) {
        if (pathTemplate == null || pathTemplate.isEmpty()) {
            return pathTemplate;
        }

        Date now = new Date();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pathTemplate);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = formatDate(now, placeholder);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String formatDate(Date date, String pattern) {
        String javaPattern = toJavaDateFormat(pattern);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(javaPattern);
            return sdf.format(date);
        } catch (Exception e) {
            // 不识别的占位符原样保留
            return "${" + pattern + "}";
        }
    }

    private static String toJavaDateFormat(String placeholder) {
        switch (placeholder) {
            case "yyyyMMdd":
                return "yyyyMMdd";
            case "yyyy-MM-dd":
                return "yyyy-MM-dd";
            case "yyyyMM":
                return "yyyyMM";
            case "yyyy":
                return "yyyy";
            case "MMdd":
                return "MMdd";
            case "HHmmss":
                return "HHmmss";
            case "HH:mm:ss":
                return "HH:mm:ss";
            default:
                return placeholder; // 透传，让 SimpleDateFormat 尝试
        }
    }
}
