package io.github.openground.land.dispatch.executor;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.base.interceptor.RestTemplateRequestInterceptor;
import io.github.openground.land.api.dto.TaskCenterRequest;
import io.github.openground.land.api.dto.TaskMonitorRequest;
import io.github.openground.land.api.dto.TaskSegmentRequest;
import io.github.openground.land.api.executor.RemoteTaskExecutor;
import io.github.openground.land.common.constants.ErrorCode;
import io.github.openground.land.dispatch.discovery.DbServiceDiscovery;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 DB 服务发现的远程任务执行器
 * <p>
 * 从 task_dispatch_active_host 表获取目标 cpsGroup 的可用实例，
 * 通过 RestTemplate 进行 HTTP 转发，配合简单轮询实现负载均衡。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Slf4j
@Component
public class DbRemoteTaskExecutor implements RemoteTaskExecutor {

    private static final String BASE_PATH = "/task/taskcenter";

    @Autowired
    private DbServiceDiscovery dbServiceDiscovery;

    private final RestTemplate restTemplate;
    private final AtomicInteger counter = new AtomicInteger(0);

    public DbRemoteTaskExecutor() {
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        // 配置拦截器：添加 encrypt=false 请求头，告知服务端内部请求报文不用解密
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new RestTemplateRequestInterceptor());
        this.restTemplate.setInterceptors(interceptors);
        
        log.info("RemoteTaskExecutor 初始化完成: [DbRemoteTaskExecutor]");
    }

    @Override
    public CommonResult<?> execute(String cpsGroup, String action, Object request) {
        // 1. 从 task_dispatch_active_host 获取可用实例
        List<String> hosts = dbServiceDiscovery.getAvailableHosts(cpsGroup);

        if (hosts == null || hosts.isEmpty()) {
            log.error("cpsGroup [{}] 无可用实例，无法转发请求: {}", cpsGroup, action);
            return CommonResult.error(ErrorCode.FAIL, "cpsGroup [" + cpsGroup + "] 无可用服务实例");
        }

        // 2. 确定目标实例 IP：如果请求中指定了 hostIp 且属于可用实例，则直接使用，不做负载均衡
        String targetHost;
        String specifiedIp = extractHostIp(request);
        if (specifiedIp != null && hosts.contains(specifiedIp)) {
            targetHost = specifiedIp;
            log.info("RemoteTaskExecutor 使用指定实例: cpsGroup={}, target={}", cpsGroup, targetHost);
        } else {
            if (specifiedIp != null) {
                log.warn("指定的实例 IP [{}] 不在可用列表中，将进行负载均衡", specifiedIp);
            }
            // 简单轮询负载均衡
            int index = Math.abs(counter.getAndIncrement() % hosts.size());
            targetHost = hosts.get(index);
        }

        // 3. 清空 cpsGroup，防止目标实例收到后再次转发形成死循环
        clearCpsGroup(request);

        // 4. 构建完整 URL
        String url = "http://" + targetHost + BASE_PATH + action;
        log.info("RemoteTaskExecutor 转发: cpsGroup={}, target={}, action={}", cpsGroup, targetHost, action);

        // 5. HTTP POST 转发
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CommonResult> response = restTemplate.postForEntity(url, entity, CommonResult.class);
            log.debug("RemoteTaskExecutor 响应: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("RemoteTaskExecutor 转发失败: cpsGroup={}, url={}", cpsGroup, url, e);
            return CommonResult.error(ErrorCode.FAIL, "远程调用失败: " + e.getMessage());
        }
    }

    /**
     * 从请求对象中提取指定的目标主机 IP
     */
    private String extractHostIp(Object request) {
        if (request instanceof TaskCenterRequest) {
            String hostIp = ((TaskCenterRequest) request).getHostIp();
            if (hostIp != null && !hostIp.isEmpty()) {
                return hostIp;
            }
        } else if (request instanceof TaskMonitorRequest) {
            String serviceUrl = ((TaskMonitorRequest) request).getServiceUrl();
            if (serviceUrl != null && !serviceUrl.isEmpty()) {
                try {
                    // 从 http://ip:port 中提取
                    return new java.net.URL(serviceUrl).getHost() + ":" + new java.net.URL(serviceUrl).getPort();
                } catch (Exception e) {
                    log.warn("解析 serviceUrl 异常: {}", serviceUrl, e);
                }
            }
        }
        return null;
    }

    /**
     * 清空请求中的 cpsGroup，防止目标实例二次转发
     */
    private void clearCpsGroup(Object request) {
        if (request instanceof TaskCenterRequest) {
            ((TaskCenterRequest) request).setCpsGroup(null);
        } else if (request instanceof TaskSegmentRequest) {
            ((TaskSegmentRequest) request).setCpsGroup(null);
        } else if (request instanceof TaskMonitorRequest) {
            ((TaskMonitorRequest) request).setCpsGroup(null);
        }
    }
}
