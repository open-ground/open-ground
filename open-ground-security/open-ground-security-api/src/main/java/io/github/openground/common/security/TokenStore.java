package io.github.openground.common.security;

import java.util.List;

/**
 * Token 存储接口
 * <p>支持数据库和 Redis 两种实现</p>
 *
 * @author open-ground
 * @version 1.0
 */
public interface TokenStore {

    /**
     * 根据 Token 查找会话
     * @param token Token 字符串
     * @return 会话实体，不存在返回 null
     */
    SessionEntity findByToken(String token);

    /**
     * 根据 ID 查找会话
     * @param id 会话ID
     * @return 会话实体，不存在返回 null
     */
    SessionEntity findById(String id);

    /**
     * 根据用户名查找会话
     * @param username 用户名
     * @return 会话列表
     */
    List<SessionEntity> findByUsername(String username);

    /**
     * 保存会话
     * @param session 会话实体
     */
    void save(SessionEntity session);

    /**
     * 更新会话
     * @param session 会话实体
     */
    void update(SessionEntity session);

    /**
     * 根据 ID 删除会话
     * @param id 会话ID
     */
    void deleteById(String id);

    /**
     * 根据用户名删除所有会话
     * @param username 用户名
     */
    void deleteByUsername(String username);

    /**
     * 清理过期会话
     */
    void cleanExpired();

    /**
     * 清理过期会话（带时间参数）
     * @param currentTime 当前时间戳（yyyyMMddHHmmss 格式）
     */
    default void cleanExpired(Long currentTime) {
        cleanExpired();
    }
}