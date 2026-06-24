package io.github.openground.common.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * API Key 响应 VO
 *
 * <p>返回给前端的 API Key 信息，含脱敏后的密钥值。
 *
 * @author open-ground
 */
@Getter
@Setter
@Accessors(chain = true)
@ToString
@NoArgsConstructor
public class ApiKeyResponseVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 密钥别名 */
    private String name;

    /** 脱敏后的 API Key（如 sk-a1b2****f6） */
    private String apiKey;

    /** 状态：0-启用，1-禁用 */
    private String status;

    /** 过期时间戳，null 表示永不过期 */
    private Long expireTime;

    /** 最后使用时间 */
    private Date lastUsedTime;

    /** 创建时间 */
    private Date createTime;
}
