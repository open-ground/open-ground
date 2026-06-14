package io.github.openground.common.security.mapper;

import io.github.openground.common.security.SessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 DAO
 * <p>MyBatis Mapper 接口，对应 sys_session 表</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Mapper
public interface SessionMapper {

    /**
     * 根据 Token 查找会话
     * @param token Token 字符串
     * @return 会话实体
     */
    SessionEntity findByToken(@Param("token") String token);

    /**
     * 根据 ID 查找会话
     * @param id 会话ID
     * @return 会话实体
     */
    SessionEntity findById(@Param("id") String id);

    /**
     * 根据用户名查找会话
     * @param username 用户名
     * @return 会话列表
     */
    List<SessionEntity> findByUsername(@Param("username") String username);

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
    void deleteById(@Param("id") String id);

    /**
     * 根据用户名删除所有会话
     * @param username 用户名
     */
    void deleteByUsername(@Param("username") String username);

    /**
     * 清理过期会话
     */
    void cleanExpired();
}