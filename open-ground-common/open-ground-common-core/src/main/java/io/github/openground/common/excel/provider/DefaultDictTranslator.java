package io.github.openground.common.excel.provider;

import io.github.openground.common.excel.spi.DictTranslator;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认字典翻译器（返回原值）
 *
 * <p>当用户未提供自定义 {@link DictTranslator} 时使用此默认实现。</p>
 *
 * @author open-ground
 */
@Slf4j
public class DefaultDictTranslator implements DictTranslator {

    @Override
    public String translate(String dictType, String value) {
        log.trace("默认字典翻译（未翻译）: dictType={}, value={}", dictType, value);
        return value;
    }
}
