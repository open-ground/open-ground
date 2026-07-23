package io.github.openground.common.notification.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 标准化通知消息：NotifierImpl 将 NotifyRequest 转换后分发给各渠道
 *
 * @author open-ground
 * @since 1.0.6
 */
@Data
@Builder
public class NotifyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一ID（由 NotifierImpl 生成，用于追踪/去重） */
    private String messageId;

    /** 接收人列表 */
    private List<String> receivers;

    /** 通知标题（已渲染完成） */
    private String title;

    /** 通知内容（已渲染完成） */
    private String content;

    /** 业务类型标识 */
    private String businessType;

    /** 业务主键 */
    private String businessKey;

    /** 发送者标识 */
    private String sender;

    /** 扩展参数 */
    private Map<String, Object> extras;

    /** 创建时间 */
    private Date createTime;

}
