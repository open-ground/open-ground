package io.github.openground.common.excel.config;

import io.github.openground.common.excel.ExcelService;
import io.github.openground.common.excel.service.impl.ExcelServiceImpl;
import io.github.openground.common.excel.spi.DictTranslator;
import io.github.openground.common.excel.provider.DefaultDictTranslator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Excel 导入导出组件自动配置
 *
 * <p>通过 {@code ground.excel.enabled=true} 开启（默认开启）。</p>
 *
 * <h3>SPI 扩展机制</h3>
 * <ul>
 *   <li>{@link DictTranslator} — 字典翻译，默认 {@link DefaultDictTranslator}（不翻译）</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ground.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExcelProperties.class)
@ComponentScan(basePackageClasses = {
        ExcelService.class,
        ExcelEntityScanner.class
})
public class ExcelAutoConfiguration {

    /**
     * 默认字典翻译器（不翻译，直接返回原值）
     */
    @Bean
    @ConditionalOnMissingBean(DictTranslator.class)
    public DictTranslator defaultDictTranslator() {
        log.debug("初始化 DefaultDictTranslator（未配置自定义字典翻译器）");
        return new DefaultDictTranslator();
    }
}
