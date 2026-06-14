package io.github.openground.common.security.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Token 提取器 SPI 接口
 * <p>从 HTTP 请求中提取 Token 字符串，业务模块可自定义实现来支持从 Cookie、参数等位置提取。</p>
 *
 * <p>默认实现 {@link DefaultTokenExtractor} 从 {@code Authorization: Bearer xxx} header 中提取。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FunctionalInterface
public interface TokenExtractor {

    /**
     * 从请求中提取 Token
     *
     * @param request HTTP 请求
     * @return Token 字符串，如果请求中无有效 Token 则返回 null
     */
    String extract(HttpServletRequest request);
}
