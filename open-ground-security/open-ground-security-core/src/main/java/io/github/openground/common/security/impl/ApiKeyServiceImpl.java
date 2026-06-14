package io.github.openground.common.security.impl;

import io.github.openground.common.security.ApiKeyDO;
import io.github.openground.common.security.ApiKeyService;
import io.github.openground.common.security.mapper.ApiKeyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * API Key 服务实现
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String KEY_PREFIX = "sk-";

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyDO generateKey(String username, String name, Date expireTime) {
        // 生成唯一 API Key 值
        String apiKeyValue = generateUniqueApiKey();

        // 构建实体
        Date now = new Date();
        ApiKeyDO apiKeyDO = new ApiKeyDO();
        apiKeyDO.setId(UUID.randomUUID().toString().replace("-", ""));
        apiKeyDO.setUsername(username);
        apiKeyDO.setApiKey(apiKeyValue);
        apiKeyDO.setName(name);
        apiKeyDO.setStatus(1); // 1-启用
        apiKeyDO.setExpireTime(expireTime);
        apiKeyDO.setCreateTime(now);
        apiKeyDO.setUpdateTime(now);
        apiKeyDO.setCreateBy(username);
        apiKeyDO.setUpdateBy(username);

        apiKeyMapper.save(apiKeyDO);
        log.info("用户 {} 生成 API Key: {} (name={})", username, maskApiKey(apiKeyValue), name);

        return apiKeyDO;
    }

    @Override
    public ApiKeyDO validateKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(KEY_PREFIX)) {
            log.warn("无效的 API Key 格式: {}", maskApiKey(apiKey));
            return null;
        }

        ApiKeyDO keyDO = apiKeyMapper.findByApiKey(apiKey);
        if (keyDO == null) {
            log.warn("API Key 不存在: {}", maskApiKey(apiKey));
            return null;
        }

        // 检查是否禁用
        if (keyDO.getStatus() != null && keyDO.getStatus() == 0) {
            log.warn("API Key 已被禁用: {}", maskApiKey(apiKey));
            return null;
        }

        // 检查是否过期
        if (keyDO.getExpireTime() != null && keyDO.getExpireTime().before(new Date())) {
            log.warn("API Key 已过期: {}", maskApiKey(apiKey));
            return null;
        }

        // 更新最后使用时间
        keyDO.setLastUsedTime(new Date());
        apiKeyMapper.update(keyDO);

        log.debug("API Key 校验通过: {}", maskApiKey(apiKey));
        return keyDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeKey(String keyId) {
        apiKeyMapper.deleteById(keyId);
        log.info("API Key 已撤销: id={}", keyId);
    }

    @Override
    public List<ApiKeyDO> getUserKeyList(String username) {
        return apiKeyMapper.findByUsername(username);
    }

    /**
     * 生成唯一的 API Key 值
     * @return 唯一的 API Key 字符串
     */
    private String generateUniqueApiKey() {
        return KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 脱敏 API Key
     * @param apiKey 原始 API Key
     * @return 脱敏后的 API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return apiKey;
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 2);
    }
}