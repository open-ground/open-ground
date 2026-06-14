package io.github.openground.common.log.service;

import io.github.openground.common.log.domain.SysOptLog;

/**
 * 操作日志发送器 SPI 接口
 * <p>业务模块通过注入不同实现来切换日志存储方式：</p>
 * <ul>
 *   <li>集成部署 → {@link io.github.openground.common.log.local.LocalLogSender}（直接 MyBatis insert）</li>
 *   <li>分离部署 → FeignLogSender（远程 Feign 调用 Auth 保存）</li>
 * </ul>
 *
 * @author open-ground
 * @version 1.0
 */
@FunctionalInterface
public interface LogSender {

    /**
     * 发送操作日志
     *
     * @param optLog 操作日志对象
     */
    void send(SysOptLog optLog);
}
