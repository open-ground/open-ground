package io.github.openground.common.logviewer;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志查看配置属性
 *
 * @author open-ground
 * @since 2026-06-18
 */
@Component
@ConfigurationProperties("ground.log")
@Data
public class LogProperties {

    /** 日志根目录，默认 ../logs */
    private String baseDir = "../logs";

    /** 集群节点列表 */
    private List<NodeConfig> nodes = new ArrayList<>();

    /**
     * 单个节点配置
     */
    @Data
    public static class NodeConfig {
        /** 节点名称 */
        private String name;
        /** 节点 IP/主机名 */
        private String host;
        /** 节点端口 */
        private int port;
    }
}
