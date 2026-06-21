package io.github.openground.common.logviewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点信息 DTO
 *
 * @author ground-auth
 * @since 2026-06-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogNodeInfo {

    /** 节点名称 */
    private String name;

    /** 节点地址 (host:port) */
    private String address;

    /** 是否为当前节点 */
    private boolean current;
}
