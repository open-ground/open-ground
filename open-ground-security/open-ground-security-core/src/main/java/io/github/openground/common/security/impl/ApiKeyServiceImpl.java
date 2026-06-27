package io.github.openground.common.security.impl;

import io.github.openground.base.exception.CommonException;
import io.github.openground.base.utils.IdUtil;
import io.github.openground.common.security.ApiKeyDO;
import io.github.openground.common.security.ApiKeyResponseVO;
import io.github.openground.common.security.ApiKeyService;
import io.github.openground.common.security.SecurityProperties;
import io.github.openground.common.security.SecurityErrorCode;
import io.github.openground.common.security.mapper.ApiKeyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * API Key 服务实现
 *
 * <p>提供 API Key 的完整生命周期管理，包括生成、校验、撤销和查询。
 *
 * @author open-ground
 * @version 2.0
 */
@Slf4j
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    /** API Key 前缀 */
    private static final String KEY_PREFIX = "sk-";

    /** API Key 随机部分长度（十六进制字符数） */
    private static final int KEY_RANDOM_LENGTH = 32;

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 十六进制字符集 */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Autowired
    private SecurityProperties securityProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponseVO generateKey(String userId, String username, String name, Long expireTime) {
        // 检查用户有效 Key 数量是否超过限制
        int maxActiveKeys = securityProperties.getApiKey().getMaxActiveKeys();
        int activeCount = apiKeyMapper.countActiveByUserId(userId);
        if (activeCount >= maxActiveKeys) {
            log.warn("用户 {} 的 API Key 数量已达上限 ({})", username, maxActiveKeys);
            throw new CommonException(SecurityErrorCode.API_KEY_LIMIT_EXCEEDED,
                    "API Key 数量已达上限（" + maxActiveKeys + "个）");
        }

        // 生成唯一 API Key 值
        String apiKeyValue = generateUniqueApiKey();

        // 构建实体
        Date now = new Date();
        ApiKeyDO apiKeyDO = new ApiKeyDO();
        apiKeyDO.setId(IdUtil.getUUID());
        apiKeyDO.setUserId(userId);
        apiKeyDO.setUsername(username);
        apiKeyDO.setApiKey(apiKeyValue);
        apiKeyDO.setName(name);
        apiKeyDO.setStatus("0");
        apiKeyDO.setExpireTime(expireTime);
        apiKeyDO.setLastUsedTime(null);
        apiKeyDO.setCreateTime(now);
        apiKeyDO.setUpdateTime(now);
        apiKeyDO.setCreateBy(username);
        apiKeyDO.setUpdateBy(username);

        apiKeyMapper.save(apiKeyDO);

        log.info("用户 {} 生成 API Key: {} (name={})", username, maskApiKey(apiKeyValue), name);

        // 返回完整明文密钥（仅创建时返回完整值）
        ApiKeyResponseVO vo = toResponseVO(apiKeyDO);
        vo.setApiKey(apiKeyValue); // 明文返回
        return vo;
    }

    @Override
    public ApiKeyDO validateKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(KEY_PREFIX)) {
            throw new CommonException(SecurityErrorCode.API_KEY_NOT_FOUND, "无效的 API Key 格式");
        }

        ApiKeyDO keyDO = apiKeyMapper.findByApiKey(apiKey);
        if (keyDO == null) {
            log.warn("API Key 不存在: {}", maskApiKey(apiKey));
            throw new CommonException(SecurityErrorCode.API_KEY_NOT_FOUND, "API Key 不存在");
        }

        // 检查是否禁用
        if ("1".equals(keyDO.getStatus())) {
            log.warn("API Key 已被禁用: {}", maskApiKey(apiKey));
            throw new CommonException(SecurityErrorCode.API_KEY_DISABLED, "API Key 已被禁用");
        }

        // 检查是否过期
        if (keyDO.getExpireTime() != null && System.currentTimeMillis() > keyDO.getExpireTime()) {
            log.warn("API Key 已过期: {}", maskApiKey(apiKey));
            throw new CommonException(SecurityErrorCode.API_KEY_EXPIRED, "API Key 已过期");
        }

        // 更新最后使用时间
        apiKeyMapper.updateLastUsedTime(keyDO.getId(), new Date());

        log.debug("API Key 校验通过: {}", maskApiKey(apiKey));
        return keyDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeKey(String id) {
        apiKeyMapper.deleteById(id);
        log.info("API Key 已撤销: id={}", id);
    }

    @Override
    public List<ApiKeyResponseVO> getUserKeyList(String userId) {
        List<ApiKeyDO> list = apiKeyMapper.findByUserId(userId);
        List<ApiKeyResponseVO> voList = new ArrayList<>(list.size());
        for (ApiKeyDO keyDO : list) {
            voList.add(toResponseVO(keyDO));
        }
        return voList;
    }

    /**
     * 生成唯一的 API Key 值
     *
     * <p>格式：sk- + 32 位随机十六进制字符
     *
     * @return 唯一的 API Key 字符串
     */
    private String generateUniqueApiKey() {
        String apiKey;
        int maxAttempts = 10;
        int attempt = 0;
        do {
            if (attempt >= maxAttempts) {
                throw new CommonException(SecurityErrorCode.API_KEY_GENERATE_ERROR, "API Key 生成失败，请重试");
            }
            apiKey = KEY_PREFIX + generateRandomHex(KEY_RANDOM_LENGTH);
            attempt++;
        } while (apiKeyMapper.countByApiKey(apiKey) > 0);
        return apiKey;
    }

    /**
     * 生成指定长度的随机十六进制字符串
     *
     * @param length 长度
     * @return 随机十六进制字符串
     */
    private String generateRandomHex(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = HEX_CHARS[SECURE_RANDOM.nextInt(HEX_CHARS.length)];
        }
        return new String(chars);
    }

    /**
     * 脱敏 API Key
     *
     * <p>格式：sk-a1b2****f6，保留前6位和后2位
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的 API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return apiKey;
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 2);
    }

    /**
     * 将 DO 转换为响应 VO（密钥值脱敏）
     *
     * @param apiKeyDO API Key 实体
     * @return 响应 VO
     */
    private ApiKeyResponseVO toResponseVO(ApiKeyDO apiKeyDO) {
        ApiKeyResponseVO vo = new ApiKeyResponseVO();
        vo.setId(apiKeyDO.getId());
        vo.setName(apiKeyDO.getName());
        vo.setApiKey(maskApiKey(apiKeyDO.getApiKey()));
        vo.setStatus(apiKeyDO.getStatus());
        vo.setExpireTime(apiKeyDO.getExpireTime());
        vo.setLastUsedTime(apiKeyDO.getLastUsedTime());
        vo.setCreateTime(apiKeyDO.getCreateTime());
        return vo;
    }
}
