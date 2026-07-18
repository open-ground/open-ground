package io.github.openground.base.interceptor;

import io.github.openground.base.utils.RequestUtil;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * <p>Title: RestTemplateRequestInterceptor</p>
 * <p>Description: </P>
 *
 * @Author:jack.zhang
 */
public class RestTemplateRequestInterceptor implements ClientHttpRequestInterceptor{

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set("encrypt","false"); // header传encrypt，告知服务端内部请求报文不用解密
        request.getHeaders().set("Authorization", RequestUtil.getTokenStr(RequestUtil.getRequest()));
        return execution.execute(request, body);
    }
}