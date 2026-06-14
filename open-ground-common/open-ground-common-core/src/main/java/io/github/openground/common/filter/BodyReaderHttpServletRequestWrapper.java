package io.github.openground.common.filter;

import org.springframework.util.StreamUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;

/**
 * 请求体缓存包装类，支持重复读取请求体流
 *
 * @author open-ground
 * @version 1.0
 */
public class BodyReaderHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private byte[] requestBody;

    /**
     * 构造时缓存请求体
     *
     * @param request 原始请求
     * @throws IOException 读取请求体异常
     */
    public BodyReaderHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        try (InputStream in = request.getInputStream()) {
            requestBody = StreamUtils.copyToByteArray(in);
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream bais = new ByteArrayInputStream(requestBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    public byte[] getBody() {
        return requestBody;
    }

    public void setBody(byte[] requestBody) {
        this.requestBody = requestBody;
    }
}
