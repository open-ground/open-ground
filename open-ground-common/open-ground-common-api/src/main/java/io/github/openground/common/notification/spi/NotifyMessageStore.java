package io.github.openground.common.notification.spi;

import io.github.openground.common.notification.domain.NotifyMessage;

/**
 * 站内信消息存储 SPI
 *
 * <p>InternalNotifyChannel 存储消息时调用此接口。
 * 业务系统实现此接口，将站内信存入自己的消息表。
 *
 * <p>示例实现（ground-auth）：
 * <pre>{@code
 * @Component
 * public class AuthNotifyMessageStore implements NotifyMessageStore {
 *     @Override
 *     public void store(NotifyMessage message, String receiver) {
 *         // 将 message 映射为 InfoDO 并入库
 *     }
 * }
 * }</pre>
 *
 * @author open-ground
 * @since 1.0.6
 */
public interface NotifyMessageStore {

    /**
     * 存储一条站内信消息
     *
     * @param message  通知消息（含标题、内容、业务信息）
     * @param receiver 接收人标识
     */
    void store(NotifyMessage message, String receiver);
}
