package io.github.openground.common.security.filter;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 Token 提取器
 * <p>从 {@code Authorization: Bearer xxx} 请求头中提取 Token。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class DefaultTokenExtractor implements TokenExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PREFIX = "bearer ";

    @Override
    public String extract(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null) {
            return null;
        }
        if (header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        if (header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
