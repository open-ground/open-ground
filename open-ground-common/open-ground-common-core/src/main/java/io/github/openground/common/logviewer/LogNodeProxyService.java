package io.github.openground.common.logviewer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.openground.common.logviewer.dto.LogFileInfo;
import io.github.openground.common.logviewer.dto.LogNodeInfo;
import io.github.openground.common.logviewer.dto.LogSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 跨节点日志代理服务
 * <p>当用户选择非当前节点时，通过 HTTP 代理请求目标节点获取日志数据。
 * <p>对于 SSE 流，使用 HttpURLConnection 以流模式读取目标节点的 SSE 响应并转发。
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Slf4j
@Service
public class LogNodeProxyService {

    private final LogProperties logProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /** 服务上下文路径，从配置注入 */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** 缓存：当前节点名称（启动时自动识别，之后不变） */
    private volatile String currentNodeName;

    public LogNodeProxyService(LogProperties logProperties, ObjectMapper objectMapper,
                               RestTemplate restTemplate) {
        this.logProperties = logProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * 自动识别当前节点
     * <p>用本机 IP 地址和 hostname 匹配 nodes 配置中的 host 字段，
     * 第一个匹配到的即为当前节点。如果都匹配不上，取第一个节点作为兜底。
     */
    private String resolveCurrentNode() {
        if (currentNodeName != null) {
            return currentNodeName;
        }
        synchronized (this) {
            if (currentNodeName != null) {
                return currentNodeName;
            }
            List<LogProperties.NodeConfig> nodes = logProperties.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                currentNodeName = "unknown";
                return currentNodeName;
            }

            // 收集本机所有可能的标识：IP 地址 + hostname
            java.util.Set<String> localIdentities = new java.util.LinkedHashSet<>();
            try {
                localIdentities.add(java.net.InetAddress.getLocalHost().getHostName());
                localIdentities.add(java.net.InetAddress.getLocalHost().getHostAddress());
            } catch (Exception e) {
                log.warn("获取本机 hostname/IP 失败: {}", e.getMessage());
            }

            // 始终加入 loopback 地址，防止 getLocalHost() 返回非 127.0.0.1 时无法匹配
            localIdentities.add("127.0.0.1");
            localIdentities.add("localhost");

            try {
                java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
                while (nics.hasMoreElements()) {
                    java.net.NetworkInterface nic = nics.nextElement();
                    if (!nic.isUp()) continue;
                    java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        java.net.InetAddress addr = addrs.nextElement();
                        if (addr.isLoopbackAddress()) continue;
                        localIdentities.add(addr.getHostAddress());
                    }
                }
            } catch (Exception e) {
                log.warn("枚举网卡地址失败: {}", e.getMessage());
            }

            log.info("本机标识: {}", localIdentities);

            // 匹配 nodes 配置
            for (LogProperties.NodeConfig nc : nodes) {
                String host = nc.getHost();
                if (host == null) continue;
                for (String identity : localIdentities) {
                    if (host.equals(identity) || host.equalsIgnoreCase(identity)) {
                        currentNodeName = nc.getName();
                        log.info("自动识别当前节点: {} (匹配 host={})", currentNodeName, host);
                        return currentNodeName;
                    }
                }
            }

            // 兜底：取第一个节点
            currentNodeName = nodes.get(0).getName();
            log.warn("无法匹配本机到任何节点配置，兜底使用第一个节点: {}", currentNodeName);
            return currentNodeName;
        }
    }

    /**
     * 获取所有节点列表
     */
    public List<LogNodeInfo> listNodes() {
        List<LogNodeInfo> nodes = new ArrayList<>();
        String currentNode = resolveCurrentNode();

        for (LogProperties.NodeConfig nc : logProperties.getNodes()) {
            LogNodeInfo info = new LogNodeInfo();
            info.setName(nc.getName());
            info.setAddress(nc.getHost() + ":" + nc.getPort());
            info.setCurrent(nc.getName().equals(currentNode));
            nodes.add(info);
        }

        return nodes;
    }

    /**
     * 获取目标节点的地址
     */
    private String resolveNodeAddress(String nodeName) {
        for (LogProperties.NodeConfig nc : logProperties.getNodes()) {
            if (nc.getName().equals(nodeName)) {
                return nc.getHost() + ":" + nc.getPort();
            }
        }
        return null;
    }

    /**
     * 判断是否为当前节点
     */
    public boolean isCurrentNode(String nodeName) {
        return resolveCurrentNode().equals(nodeName);
    }

    /**
     * 代理 GET 请求到目标节点，返回 Map 结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> proxyGet(String nodeName, String path) {
        String address = resolveNodeAddress(nodeName);
        if (address == null) {
            log.warn("未知节点: {}", nodeName);
            return null;
        }

        String url = "http://" + address + contextPath + "/log" + path;
        log.debug("代理请求: GET {}", url);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("代理请求失败: {} -> {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 代理获取日志文件列表
     */
    public List<LogFileInfo> proxyListFiles(String nodeName, String date) {
        Map<String, Object> result = proxyGet(nodeName, "/files?date=" + date);
        if (result == null || result.get("data") == null) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(result.get("data"), new TypeReference<List<LogFileInfo>>() {});
    }

    /**
     * 代理获取日期列表
     */
    public List<String> proxyListDates(String nodeName) {
        Map<String, Object> result = proxyGet(nodeName, "/dates");
        if (result == null || result.get("data") == null) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(result.get("data"), new TypeReference<List<String>>() {});
    }

    /**
     * 代理读取日志内容
     */
    public List<String> proxyReadContent(String nodeName, String date, String fileName, long offset, int limit) {
        String path = "/content?date=" + date + "&fileName=" + fileName + "&offset=" + offset + "&limit=" + limit;
        Map<String, Object> result = proxyGet(nodeName, path);
        if (result == null || result.get("data") == null) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(result.get("data"), new TypeReference<List<String>>() {});
    }

    /**
     * 代理搜索日志
     */
    public List<String> proxySearch(String nodeName, String date, String fileName, LogSearchRequest request) {
        StringBuilder path = new StringBuilder("/search?date=" + date + "&fileName=" + fileName);
        if (request.getKeyword() != null) {
            path.append("&keyword=").append(request.getKeyword());
        }
        if (request.getLevel() != null) {
            path.append("&level=").append(request.getLevel());
        }
        path.append("&limit=").append(request.getLimit());
        path.append("&offset=").append(request.getOffset());

        Map<String, Object> result = proxyGet(nodeName, path.toString());
        if (result == null || result.get("data") == null) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(result.get("data"), new TypeReference<List<String>>() {});
    }

    /**
     * 代理 SSE tail 请求
     * <p>连接目标节点的 SSE 端点，将事件流转发到当前请求的 SseEmitter。
     *
     * @param nodeName  目标节点名称
     * @param date      日期
     * @param fileName  文件名
     * @param sessionId 会话标识
     * @return SseEmitter（转发流）
     */
    public SseEmitter proxyTail(String nodeName, String date, String fileName, String sessionId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String address = resolveNodeAddress(nodeName);
        if (address == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("未知节点: " + nodeName));
                emitter.complete();
            } catch (IOException e) {
                // ignore
            }
            return emitter;
        }

        String url = "http://" + address + contextPath + "/log/tail?date=" + date
                + "&fileName=" + fileName + "&sessionId=" + sessionId;
        log.info("代理 SSE 连接: {}", url);

        Thread proxyThread = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL targetUrl = new URL(url);
                conn = (HttpURLConnection) targetUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30 * 60 * 1000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    emitter.send(SseEmitter.event().name("error")
                            .data("目标节点返回错误: HTTP " + responseCode));
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder dataBuffer = new StringBuilder();
                    String eventName = "log";

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            eventName = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            dataBuffer.append(line.substring(5));
                        } else if (line.isEmpty() && dataBuffer.length() > 0) {
                            // SSE 事件结束，转发
                            try {
                                emitter.send(SseEmitter.event()
                                        .name(eventName)
                                        .data(dataBuffer.toString()));
                            } catch (IOException e) {
                                // 前端断开
                                break;
                            }
                            dataBuffer.setLength(0);
                            eventName = "log";
                        }
                    }
                }
            } catch (Exception e) {
                log.error("代理 SSE 连接异常: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("代理连接异常: " + e.getMessage()));
                } catch (IOException ex) {
                    // ignore
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                try {
                    emitter.complete();
                } catch (Exception e) {
                    // ignore
                }
            }
        }, "log-proxy-tail-" + sessionId);
        proxyThread.setDaemon(true);
        proxyThread.start();

        emitter.onCompletion(() -> proxyThread.interrupt());
        emitter.onTimeout(() -> proxyThread.interrupt());
        emitter.onError(e -> proxyThread.interrupt());

        return emitter;
    }
}
