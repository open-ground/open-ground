package io.github.openground.common.notification.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 单个通知渠道的发送结果
 *
 * @author open-ground
 * @since 1.0.6
 */
@Data
@Builder
public class ChannelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道类型 */
    private String channelType;

    /** 是否发送成功 */
    private boolean success;

    /** 成功时的消息ID（渠道返回的） */
    private String channelMessageId;

    /** 失败时的错误信息 */
    private String errorMessage;

}
