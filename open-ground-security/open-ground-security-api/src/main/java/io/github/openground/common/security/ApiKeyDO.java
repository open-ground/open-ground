package io.github.openground.common.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * API Key 实体
 * <p>映射 sys_api_key 表</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Data
public class ApiKeyDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Key ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** API Key */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /** Key 名称 */
    private String name;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 过期时间 */
    private Date expireTime;

    /** 最后使用时间 */
    private Date lastUsedTime;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 创建人 */
    private String createBy;

    /** 修改人 */
    private String updateBy;
}