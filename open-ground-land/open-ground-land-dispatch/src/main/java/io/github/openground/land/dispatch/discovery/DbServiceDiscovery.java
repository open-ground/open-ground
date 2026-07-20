package io.github.openground.land.dispatch.discovery;

import io.github.openground.land.service.ActiveHostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 DB 的服务发现 — 从 task_dispatch_active_host 表查询可用实例
 * <p>
 * 根据 cpsGroup 查询活跃时间在阈值内的主机列表，
 * 不依赖注册中心（Nacos/Eureka），实现轻量级服务发现。
 * </p>
 *
 * @author jack.zhang
 * @since 2026-06-26
 */
@Slf4j
@Component
public class DbServiceDiscovery {

    @Autowired
    private ActiveHostService activeHostService;

    /**
     * 获取指定 cpsGroup 下可用的服务实例主机 IP 列表
     * <p>活跃判定：心跳时间在阈值内（默认最近 3 分钟）</p>
     *
     * @param cpsGroup    调度组
     * @return 可用主机 IP 列表
     */
    public List<String> getAvailableHosts(String cpsGroup) {
        if (cpsGroup == null || cpsGroup.isEmpty()) {
            log.warn("cpsGroup 为空，无法查询可用主机");
            return Collections.emptyList();
        }
        try {
            List<String> hosts = activeHostService.getAvailableHostIps(cpsGroup);
            log.debug("cpsGroup [{}] 可用主机: {}", cpsGroup, hosts);
            return hosts;
        } catch (Exception e) {
            log.error("查询 cpsGroup [{}] 可用主机异常", cpsGroup, e);
            return Collections.emptyList();
        }
    }

}
