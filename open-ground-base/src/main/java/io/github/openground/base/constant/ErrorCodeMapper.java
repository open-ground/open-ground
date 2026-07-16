package io.github.openground.base.constant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 错误码映射器 —— 将内部错误码映射为外部错误码/消息。
 *
 * <p>启动时自动扫描 classpath 下所有 {@code error-code*.properties} 文件，
 * 合并建立 code→target 和 code→message 的映射关系。</p>
 *
 * <h3>配置文件格式</h3>
 * <pre>{@code
 * # <内部码>.target=<外部码>
 * # <内部码>.message=<外部消息>
 * 0401.target=AUTH_401
 * 0401.message=请先登录系统
 * }</pre>
 *
 * <h3>多模块约定</h3>
 * 各业务模块在 {@code src/main/resources/} 下放置符合命名约定的文件即可：
 * <ul>
 *   <li>{@code error-code-security.properties}</li>
 *   <li>{@code error-code-xxx.properties}</li>
 * </ul>
 *
 * @author open-ground
 */
@Slf4j
@Component
public class ErrorCodeMapper implements InitializingBean {

    private final Properties codeMapping = new Properties();
    private final Properties messageMapping = new Properties();

    @Override
    public void afterPropertiesSet() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:error-code*.properties");

            if (resources.length == 0) {
                log.info("未找到任何 error-code*.properties 文件，错误码映射功能未启用");
                return;
            }

            log.info("扫描到 {} 个错误码配置文件:", resources.length);
            int totalFiles = 0;
            for (Resource resource : resources) {
                log.info("  └─ {}", resource.getURL());
                Properties props = new Properties();
                props.load(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
                int count = 0;
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    if (key.endsWith(".target")) {
                        codeMapping.put(key.replace(".target", ""), value);
                        count++;
                    } else if (key.endsWith(".message")) {
                        messageMapping.put(key.replace(".message", ""), value);
                        count++;
                    }
                }
                totalFiles++;
                log.info("  └─ 加载 {} 条映射", count);
            }
            log.info("错误码映射初始化完成: code映射 {} 条, message映射 {} 条",
                    codeMapping.size(), messageMapping.size());
        } catch (Exception e) {
            log.error("扫描 error-code*.properties 失败", e);
        }
    }

    /**
     * 映射内部错误码 → 外部错误码和消息。
     *
     * @param internalCode    内部错误码
     * @param internalMessage 内部错误消息（可为 null）
     * @return [外部码, 外部消息]，未配置映射时返回原始值
     */
    public String[] map(String internalCode, String internalMessage) {
        String externalCode = codeMapping.getProperty(internalCode, internalCode);
        String externalMessage = messageMapping.getProperty(internalCode,
                internalMessage != null ? internalMessage : internalCode);
        return new String[]{externalCode, externalMessage};
    }

    /**
     * 仅映射 code。
     *
     * @param internalCode 内部错误码
     * @return 外部错误码，未配置时返回原值
     */
    public String mapCode(String internalCode) {
        return codeMapping.getProperty(internalCode, internalCode);
    }

    /**
     * 仅映射 message。
     *
     * @param internalCode    内部错误码
     * @param defaultMessage  未映射时的默认消息
     * @return 外部消息，未配置时返回 defaultMessage
     */
    public String mapMessage(String internalCode, String defaultMessage) {
        return messageMapping.getProperty(internalCode, defaultMessage);
    }
}
