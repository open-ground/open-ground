package io.github.openground.common.notification;

import io.github.openground.common.notification.domain.ChannelResult;
import io.github.openground.common.notification.domain.NotifyChannelType;
import io.github.openground.common.notification.domain.NotifyMessage;
import io.github.openground.common.notification.spi.NotifyChannel;
import io.github.openground.common.notification.spi.NotifyMessageStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 站内信通知渠道实现
 *
 * <p>将通知消息通过 {@link NotifyMessageStore} 持久化存储，作为系统内部消息。
 * 这是默认启用的通知渠道，适用于系统内部消息通知。
 *
 * <p>业务系统需实现 {@link NotifyMessageStore} 接口并注册为 Spring Bean，
 * 将消息存入自己的消息表（如 ground-auth 的 InfoDO 表）。
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
public class InternalNotifyChannel implements NotifyChannel {

    private final NotifyMessageStore messageStore;

    public InternalNotifyChannel(NotifyMessageStore messageStore) {
        this.messageStore = messageStore;
    }

    @Override
    public String channelType() {
        return NotifyChannelType.INTERNAL;
    }

    @Override
    public int getOrder() {
        // 站内信作为基础渠道，优先级最高
        return 1;
    }

    @Override
    public ChannelResult send(NotifyMessage message) {
        if (messageStore == null) {
            log.warn("NotifyMessageStore 未配置，站内信消息将被丢弃。"
                    + "请实现 NotifyMessageStore 接口并注册为 Spring Bean");
            return ChannelResult.builder()
                    .channelType(channelType())
                    .success(false)
                    .errorMessage("NotifyMessageStore not configured")
                    .build();
        }

        try {
            List<String> receivers = message.getReceivers();
            if (receivers == null || receivers.isEmpty()) {
                log.warn("站内信接收人列表为空，跳过发送");
                return ChannelResult.builder()
                        .channelType(channelType())
                        .success(false)
                        .errorMessage("receivers is empty")
                        .build();
            }

            log.debug("开始发送站内信 - 消息ID: {}, 接收人数: {}",
                    message.getMessageId(), receivers.size());

            int successCount = 0;
            for (String receiver : receivers) {
                try {
                    messageStore.store(message, receiver);
                    successCount++;
                } catch (Exception e) {
                    log.error("站内信存储失败 - 接收人: {}", receiver, e);
                }
            }

            log.info("站内信发送完成 - 消息ID: {}, 成功: {}/{}",
                    message.getMessageId(), successCount, receivers.size());
            return ChannelResult.builder()
                    .channelType(channelType())
                    .success(successCount > 0)
                    .build();

        } catch (Exception e) {
            log.error("站内信发送异常", e);
            return ChannelResult.builder()
                    .channelType(channelType())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

}
