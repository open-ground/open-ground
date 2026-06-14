package io.github.openground.common.security;

import java.util.Date;
import java.util.List;

/**
 * API Key 服务接口
 *
 * @author open-ground
 * @version 1.0
 */
public interface ApiKeyService {

    /**
     * 生成 API Key
     * @param username 用户名
     * @param name Key 名称
     * @param expireTime 过期时间
     * @return API Key 实体
     */
    ApiKeyDO generateKey(String username, String name, Date expireTime);

    /**
     * 验证 API Key
     * @param apiKey API Key 字符串
     * @return API Key 实体，验证失败返回 null
     */
    ApiKeyDO validateKey(String apiKey);

    /**
     * 撤销 API Key
     * @param keyId Key ID
     */
    void revokeKey(String keyId);

    /**
     * 获取用户的 API Key 列表
     * @param username 用户名
     * @return API Key 列表
     */
    List<ApiKeyDO> getUserKeyList(String username);
}