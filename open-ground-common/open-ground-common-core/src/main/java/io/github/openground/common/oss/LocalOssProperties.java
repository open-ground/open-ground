package io.github.openground.common.oss;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Paths;

/**
 * 本地文件存储配置属性
 * <p>
 * 通过 {@code ground.oss.type=local} 启用本地文件存储模式。
 * 集群模式下，通过 {@code fileNodeUrl} 指定文件节点地址，其他节点自动转发文件操作到该节点。
 * </p>
 *
 * @author open-ground
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ground.oss.local")
public class LocalOssProperties {

    /**
     * 文件存储根路径（默认: {user.dir}/../storage）
     */
    private String path;

    /**
     * 文件节点完整地址，如 http://192.168.1.100:8080/auth/uaa
     * <p>
     * 配置后，当前节点会判断自己是否是文件节点：
     * <ul>
     *   <li>本机 IP 匹配 fileNodeUrl 中的 host → 本地模式，直接读写磁盘</li>
     *   <li>本机 IP 不匹配 → 远程模式，通过 RestTemplate 转发到文件节点</li>
     * </ul>
     * 不配置时，默认为单机模式，自身就是文件节点。
     * </p>
     */
    private String fileNodeUrl;

    public String getPath() {
        if (path == null || path.isEmpty()) {
            path = Paths.get(System.getProperty("user.dir"), "..", "fileStorage")
                    .normalize()
                    .toString();
        }
        return path;
    }
}