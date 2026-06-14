package io.github.openground.common.security.mapper;

import io.github.openground.common.security.ApiKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * API Key DAO
 * <p>MyBatis Mapper 接口，对应 sys_api_key 表</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Mapper
public interface ApiKeyMapper {

    /**
     * 根据 API Key 查找
     * @param apiKey API Key 字符串
     * @return API Key 实体
     */
    ApiKeyDO findByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据 ID 查找
     * @param id Key ID
     * @return API Key 实体
     */
    ApiKeyDO findById(@Param("id") String id);

    /**
     * 根据用户名查找所有 API Key
     * @param username 用户名
     * @return API Key 列表
     */
    List<ApiKeyDO> findByUsername(@Param("username") String username);

    /**
     * 保存 API Key
     * @param apiKeyDO API Key 实体
     */
    void save(ApiKeyDO apiKeyDO);

    /**
     * 更新 API Key
     * @param apiKeyDO API Key 实体
     */
    void update(ApiKeyDO apiKeyDO);

    /**
     * 根据 ID 删除 API Key
     * @param id Key ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 根据用户名删除所有 API Key
     * @param username 用户名
     */
    void deleteByUsername(@Param("username") String username);
}