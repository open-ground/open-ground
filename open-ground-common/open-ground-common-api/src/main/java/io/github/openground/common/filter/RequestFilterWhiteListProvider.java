package io.github.openground.common.filter;

import java.util.List;

/**
 * CommonRequestFilter 白名单提供者 SPI 接口
 *
 * <p>业务系统可实现此接口并声明为 Spring Bean，向请求过滤器添加自定义白名单路径。
 * 所有实现的白名单将与配置文件中的 whiteList 合并。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FunctionalInterface
public interface RequestFilterWhiteListProvider {

    /**
     * 获取白名单 URL 模式列表（Ant 路径模式）
     * <p>匹配的 URL 将跳过请求过滤器的所有检查（Token 校验、解密验签、URL 检查等）。</p>
     *
     * @return 白名单列表，不可返回 null
     */
    List<String> getWhiteList();
}
