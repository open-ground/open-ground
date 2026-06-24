package io.github.openground.common.security;

import java.util.List;

/**
 * API Key 服务接口
 *
 * <p>提供 API Key 的生成、校验、撤销和查询功能。
 *
 * @author open-ground
 * @version 2.0
 */
public interface ApiKeyService {

    /**
     * 生成 API Key
     *
     * <p>生成格式为 sk- + 32位随机十六进制字符的 API Key，
     * 检查唯一性后保存到数据库并返回完整密钥值。
     *
     * @param userId     用户 ID
     * @param username   用户名
     * @param name       Key 名称
     * @param expireTime 过期时间戳（毫秒），null 表示永不过期
     * @return 包含完整明文密钥的响应 VO
     */
    ApiKeyResponseVO generateKey(String userId, String username, String name, Long expireTime);

    /**
     * 校验 API Key 有效性
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>查询 API Key 是否存在</li>
     *   <li>检查是否被禁用（status = '1'）</li>
     *   <li>检查是否已过期</li>
     *   <li>更新最后使用时间</li>
     * </ol>
     *
     * @param apiKey API Key 值
     * @return 有效的 API Key 实体
     * @throws io.github.openground.base.exception.CommonException 当 key 不存在/已禁用/已过期时抛出
     */
    ApiKeyDO validateKey(String apiKey);

    /**
     * 撤销 API Key（物理删除）
     *
     * @param id API Key ID
     */
    void revokeKey(String id);

    /**
     * 获取用户的 API Key 列表（密钥值脱敏显示）
     *
     * @param userId 用户 ID
     * @return 脱敏后的 API Key 列表
     */
    List<ApiKeyResponseVO> getUserKeyList(String userId);
}