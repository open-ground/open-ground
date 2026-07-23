package io.github.openground.common.notification.spi;

import io.github.openground.common.notification.domain.ChannelResult;
import io.github.openground.common.notification.domain.NotifyMessage;

/**
 * 通知渠道 SPI
 *
 * <p>实现此接口并注册为 Spring Bean，组件自动发现并按优先级管理。
 * 每个渠道实现一个消息通道（站内信、钉钉、企微、邮件、短信等）。
 *
 * <p>示例实现：
 * <pre>{@code
 * @Component
 * public class DingTalkChannel implements NotifyChannel {
 *     @Override
 *     public String channelType() { return "DINGTALK"; }
 *
 *     @Override
 *     public ChannelResult send(NotifyMessage message) {
 *         // 将 message 转为钉钉消息格式并发送
 *         return ChannelResult.builder().channelType(channelType()).success(true).build();
 *     }
 * }
 * }</pre>
 *
 * @author open-ground
 * @since 1.0.6
 */
public interface NotifyChannel {

    /**
     * 获取渠道类型标识
     * <p>用于区分不同的通知渠道，如 {@code INTERNAL}、{@code DINGTALK}、{@code WECOM}、{@code EMAIL}、{@code SMS} 等。
     *
     * @return 渠道类型标识
     */
    String channelType();

    /**
     * 发送通知
     *
     * @param message 标准化通知消息（标题、内容已由 Notifier 完成模板渲染）
     * @return 该渠道的发送结果
     */
    ChannelResult send(NotifyMessage message);

    /**
     * 判断该渠道是否可用
     * <p>可用于通过配置动态控制渠道的启用/禁用状态。
     *
     * @return true-可用，false-不可用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取渠道优先级
     * <p>数值越小越先执行，默认为 100。
     *
     * @return 优先级数值
     */
    default int getOrder() {
        return 100;
    }
}
