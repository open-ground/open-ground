package io.github.openground.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话实体
 * <p>映射 sys_session 表</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
public class SessionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String id;

    /** 会话数据（JSON 格式的 UserDetails） */
    private String sessionData;

    /** Token */
    private String token;

    /** 用户名 */
    private String username;

    /** 授权类型（password/sms/apikey 等） */
    private String grantType;

    /** 创建时间 */
    private Date createTime;

    /** 最后访问时间 */
    private Date lastAccessTime;

    /** 过期时间（yyyyMMddHHmmss 格式） */
    private Long expireTime;

    /** 客户端 IP */
    private String host;
}