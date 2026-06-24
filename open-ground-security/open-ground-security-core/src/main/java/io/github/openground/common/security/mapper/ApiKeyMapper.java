package io.github.openground.common.security.mapper;

import io.github.openground.common.security.ApiKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * API Key 数据访问接口
 *
 * <p>负责 sys_api_key 表的数据库操作，采用物理删除方式。
 *
 * @author open-ground
 * @version 2.0
 */
@Mapper
public interface ApiKeyMapper {

    /**
     * 新增 API Key 记录
     *
     * @param apiKeyDO API Key 实体
     * @return 影响行数
     */
    int save(ApiKeyDO apiKeyDO);

    /**
     * 根据 API Key 值查询
     *
     * @param apiKey API Key 值
     * @return API Key 实体
     */
    ApiKeyDO findByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据 ID 查找
     *
     * @param id Key ID
     * @return API Key 实体
     */
    ApiKeyDO findById(@Param("id") String id);

    /**
     * 根据用户 ID 查询该用户的所有 API Key
     *
     * @param userId 用户 ID
     * @return API Key 列表
     */
    List<ApiKeyDO> findByUserId(@Param("userId") String userId);

    /**
     * 更新 API Key
     *
     * @param apiKeyDO API Key 实体
     * @return 影响行数
     */
    int update(ApiKeyDO apiKeyDO);

    /**
     * 更新最后使用时间
     *
     * @param id           API Key ID
     * @param lastUsedTime 最后使用时间
     * @return 影响行数
     */
    int updateLastUsedTime(@Param("id") String id, @Param("lastUsedTime") Date lastUsedTime);

    /**
     * 物理删除 API Key
     *
     * @param id API Key ID
     * @return 影响行数
     */
    int deleteById(@Param("id") String id);

    /**
     * 根据用户名删除所有 API Key
     *
     * @param username 用户名
     * @return 影响行数
     */
    int deleteByUsername(@Param("username") String username);

    /**
     * 检查 API Key 值是否存在
     *
     * @param apiKey API Key 值
     * @return 记录数
     */
    int countByApiKey(@Param("apiKey") String apiKey);

    /**
     * 统计用户当前有效的 API Key 数量
     *
     * @param userId 用户 ID
     * @return 有效记录数
     */
    int countActiveByUserId(@Param("userId") String userId);
}