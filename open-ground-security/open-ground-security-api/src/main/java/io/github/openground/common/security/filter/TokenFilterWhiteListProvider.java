package io.github.openground.common.security.filter;

import java.util.List;

/**
 * AuthTokenManagerFilter 白名单提供者 SPI 接口
 *
 * <p>业务系统可实现此接口并声明为 Spring Bean，向 Token 鉴权过滤器添加自定义白名单路径。
 * 所有实现的白名单将与配置文件中的 whiteList 合并。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FunctionalInterface
public interface TokenFilterWhiteListProvider {

    /**
     * 获取白名单 URL 模式列表（Ant 路径模式）
     * <p>匹配的 URL 将跳过 Token 鉴权过滤器的校验。</p>
     *
     * @return 白名单列表，不可返回 null
     */
    List<String> getWhiteList();
}
