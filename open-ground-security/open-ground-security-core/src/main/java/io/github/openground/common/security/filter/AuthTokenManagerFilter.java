package io.github.openground.common.security.filter;

import com.alibaba.fastjson.JSON;
import io.github.openground.base.constant.ErrorCode;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.security.SecurityContextHolder;
import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenManager;
import io.github.openground.common.security.config.TokenFilterProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Token 鉴权过滤器
 * <p>拦截 HTTP 请求，提取并校验 Token，通过 {@link TokenManager} 验证 Token 有效性。
 * 校验通过后将当前会话设置到 ThreadLocal，请求结束后自动清除。</p>
 *
 * <p>白名单路径跳过校验，可通过 {@link TokenFilterProperties} 配置。</p>
 *
 * <p>Token 提取策略可通过 {@link TokenExtractor} SPI 接口自定义。</p>
 *
 * @author open-ground
 * @version 1.0
 * @see TokenFilterProperties
 * @see TokenExtractor
 * @see DefaultTokenExtractor
 */
@Slf4j
public class AuthTokenManagerFilter implements Filter {

    private final TokenManager tokenManager;
    private final TokenExtractor tokenExtractor;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Setter
    protected List<String> whiteList;

    @Setter
    private boolean enabled = true;

    /**
     * 白名单提供者列表（由业务模块 SPI 实现）
     */
    private List<TokenFilterWhiteListProvider> whiteListProviders;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        initWhiteList(whiteList);
        // 合并 SPI 白名单提供者的白名单
        if (whiteListProviders != null) {
            for (TokenFilterWhiteListProvider provider : whiteListProviders) {
                List<String> providerWhiteList = provider.getWhiteList();
                if (providerWhiteList != null) {
                    if (whiteList == null) {
                        whiteList = new ArrayList<>();
                    }
                    whiteList.addAll(providerWhiteList);
                }
            }
        }
        Filter.super.init(filterConfig);
    }

    protected void initWhiteList(List<String> whiteList) {

    }

    /**
     * 设置白名单提供者列表
     *
     * @param whiteListProviders 白名单提供者列表
     */
    public void setWhiteListProviders(List<TokenFilterWhiteListProvider> whiteListProviders) {
        this.whiteListProviders = whiteListProviders;
    }

    public AuthTokenManagerFilter(TokenManager tokenManager, TokenExtractor tokenExtractor) {
        this.tokenManager = tokenManager;
        this.tokenExtractor = tokenExtractor;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String uri = request.getServletPath();

        // 白名单路径直接放行
        if (isWhiteListed(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 Token
        String token = tokenExtractor.extract(request);
        if (token == null || token.isEmpty()) {
            log.warn("缺少 Token: {} {}", request.getMethod(), uri);
            writeUnauthorized(response, "缺少 Token，请在 Authorization header 中携带 Bearer token");
            return;
        }

        try {
            // API Key 认证分支：以 sk- 开头的走 API Key 校验
            if (token.startsWith("sk-")) {
                tokenManager.validateApiKey(token);
            } else {
                // 常规 Token 校验
                SessionEntity session = tokenManager.validateAndRefreshToken(token, uri);
                if (session == null) {
                    log.warn("Token 无效或已过期: {} {}", request.getMethod(), uri);
                    writeUnauthorized(response, "Token 无效或已过期");
                    return;
                }
                // 设置当前会话到 ThreadLocal
                tokenManager.setCurrentSession(session);
                // 同步设置 SecurityContextHolder
                SecurityContextHolder.setCurrentUser(tokenManager.getCurrentUser());
            }

            chain.doFilter(request, response);
        } finally {
            // 请求结束后清除上下文，防止内存泄漏
            tokenManager.clearCurrentSession();
            SecurityContextHolder.clear();
        }
    }

    /**
     * 判断请求 URI 是否在白名单中
     */
    private boolean isWhiteListed(String uri) {
        if (whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        for (String pattern : whiteList) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 401 未授权响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        CommonResult<Void> result = CommonResult.error(ErrorCode.NO_AUTH, message);
        response.getOutputStream().write(JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8));
    }
}
