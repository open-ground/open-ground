package io.github.openground.common.notification.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 通知响应：聚合所有渠道的发送结果
 *
 * @author open-ground
 * @since 1.0.6
 */
@Data
@Builder
public class NotifyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否全部发送成功 */
    private boolean allSuccess;

    /** 各渠道的发送结果，key 为渠道类型，value 为对应结果 */
    private Map<String, ChannelResult> channelResults;

    /** 成功渠道数 */
    private int successCount;

    /** 失败渠道数 */
    private int failCount;

}
