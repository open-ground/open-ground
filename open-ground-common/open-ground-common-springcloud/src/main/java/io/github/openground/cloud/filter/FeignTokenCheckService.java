package io.github.openground.cloud.filter;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.common.filter.TokenCheckService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TokenCheckService} 的 Feign 远程调用实现
 * <p>适用于 Service 模式，通过 Feign 远程调用 Auth 服务进行 token 校验。</p>
 *
 * @author open-ground
 */
@Slf4j
@RequiredArgsConstructor
public class FeignTokenCheckService implements TokenCheckService {

    private final AuthFeignClient feignClient;

    @Override
    public void check(HttpServletRequest request) throws Exception {
        try {
            CommonResult result = feignClient.tokenCheck();
            if (!"0000".equals(result.getCode())) {
                log.warn("远程 Token 校验失败: code={}, message={}", result.getCode(), result.getMessage());
                throw new RuntimeException("token认证失败");
            }
            log.debug("远程 Token 校验通过");
        } catch (Exception e) {
            log.error("远程 Token 校验异常", e);
            throw e;
        }
    }
}
