package io.github.openground.common.excel.spi;

/**
 * 字典翻译器 SPI 接口
 *
 * <p>导出时：将数据库存储的 code 翻译为用户可读的 label（如 "0" → "男"）。</p>
 * <p>导入时：将 Excel 中的 label 翻译为数据库存储的 code（如 "男" → "0"）。</p>
 *
 * <h3>自定义实现</h3>
 * <pre>{@code
 * @Component
 * public class MyDictTranslator implements DictTranslator {
 *     public String translate(String dictType, String code) {
 *         if ("gender".equals(dictType)) return "0".equals(code) ? "男" : "女";
 *         return code;
 *     }
 * }
 * }</pre>
 *
 * <p>默认提供 {@code DefaultDictTranslator}（返回原值），</p>
 * <p>业务方只需声明一个 Spring Bean 即可覆盖。</p>
 *
 * @author open-ground
 * @see io.github.openground.common.excel.provider.DefaultDictTranslator
 */
@FunctionalInterface
public interface DictTranslator {

    /**
     * 翻译字典值
     *
     * @param dictType 字典类型（如 "gender", "dept"）
     * @param value    待翻译的值（导出时为 code，导入时为 label）
     * @return 翻译后的值
     */
    String translate(String dictType, String value);
}
