package io.github.openground.cloud.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.openground.base.utils.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // template.header(HttpHeaders.AUTHORIZATION, "token");
        log.debug("**************************FeignHeaderInterceptor开始");
        HttpServletRequest request = RequestUtil.getRequest();
        String token = RequestUtil.getTokenStr(request);

        if (request != null) {
            template.header("Menu-ID", RequestUtil.getHeaders(request).get("menu-id"));
            template.header("srcUrl", request.getServletPath());
        }

        log.debug("----获取到的authorization内容为[" + token + "]");
        template.header("authorization", token);
        template.header("encrypt", "false"); // header传encrypt，告知服务端内部请求报文不用解密
        log.debug("**************************FeignHeaderInterceptor结束");
    }
}
