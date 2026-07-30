package io.github.openground.cloud.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.openground.base.utils.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        log.debug("FeignHeaderInterceptor 开始");
        HttpServletRequest request = RequestUtil.getRequest();
        String token = RequestUtil.getToken();

        if (request != null) {
            template.header("Menu-ID", RequestUtil.getHeaders(request).get("menu-id"));
            template.header("srcUrl", request.getServletPath());
        }

        log.debug("获取到的 authorization 内容为 [{}]", token);
        template.header("authorization", token);
        // header 传 encrypt，告知服务端内部请求报文不用解密
        template.header("encrypt", "false");
        log.debug("FeignHeaderInterceptor 结束");
    }
}