package io.github.openground.common.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Token 校验 SPI 接口
 *
 * <p>业务模块需提供此接口的实现 Bean（如通过 Feign 调用 auth 服务进行 token 校验），
 * {@link CommonRequestFilter} 会在 {@code tokenCheckEnabled=true} 时自动调用。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FunctionalInterface
public interface TokenCheckService {

    /**
     * Token 校验
     *
     * @param request 当前 HTTP 请求
     * @throws Exception 校验失败时抛出异常，异常信息将作为错误消息返回客户端
     */
    void check(HttpServletRequest request) throws Exception;
}
