package io.github.openground.common.notification.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通知请求：业务系统调用时传入的参数
 *
 * @author open-ground
 * @since 1.0.6
 */
@Data
@Builder
public class NotifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收人列表（用户ID / 手机号 / 邮箱，由渠道自行解释） */
    private List<String> receivers;

    /** 通知标题（可含 {{var}} 占位符） */
    private String title;

    /** 通知内容（可含 {{var}} 占位符） */
    private String content;

    /** 模板参数：用于渲染 title 和 content 中的 {{var}} 占位符 */
    private Map<String, Object> templateParams;

    /** 指定渠道类型（为空则发送所有已启用的渠道） */
    private Set<String> channels;

    /** 业务类型标识，用于区分不同业务场景的通知 */
    private String businessType;

    /** 业务主键 */
    private String businessKey;

    /** 发送者标识（为空默认 system） */
    private String sender;

    /** 扩展参数，用于传递渠道特有参数 */
    private Map<String, Object> extras;

}
