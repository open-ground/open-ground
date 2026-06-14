package io.github.openground.common.security.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Token 存储实现
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@Component("redisTokenStore")
@ConditionalOnProperty(name = "ground.security.token-store", havingValue = "redis")
public class RedisTokenStore implements TokenStore {

    private static final String SESSION_PREFIX = "security:session:";
    private static final String TOKEN_PREFIX = "security:token:";
    private static final String USER_SESSION_PREFIX = "security:user:sessions:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("RedisTokenStore initialized - TokenStore type: Redis, Bean name: redisTokenStore");
    }

    @Override
    public SessionEntity findByToken(String token) {
        String key = TOKEN_PREFIX + token;
        Object sessionObj = redisTemplate.opsForValue().get(key);
        if (sessionObj == null) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionObj.toString(), SessionEntity.class);
        } catch (Exception e) {
            log.error("Failed to deserialize session by token: {}", token, e);
            return null;
        }
    }

    @Override
    public SessionEntity findById(String id) {
        String key = SESSION_PREFIX + id;
        Object sessionObj = redisTemplate.opsForValue().get(key);
        if (sessionObj == null) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionObj.toString(), SessionEntity.class);
        } catch (Exception e) {
            log.error("Failed to deserialize session by id: {}", id, e);
            return null;
        }
    }

    @Override
    public List<SessionEntity> findByUsername(String username) {
        String key = USER_SESSION_PREFIX + username;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(key);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SessionEntity> sessions = new ArrayList<>();
        for (Object sessionId : sessionIds) {
            SessionEntity session = findById(sessionId.toString());
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    @Override
    public void save(SessionEntity session) {
        try {
            String sessionKey = SESSION_PREFIX + session.getId();
            String tokenKey = TOKEN_PREFIX + session.getToken();
            String sessionJson = objectMapper.writeValueAsString(session);

            // expireTime 是 yyyyMMddHHmmss 格式的 Long，需转换回真实时间戳计算 TTL
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            long expireMillis = sdf.parse(String.valueOf(session.getExpireTime())).getTime();
            long ttl = (expireMillis - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                redisTemplate.opsForValue().set(sessionKey, sessionJson, ttl, TimeUnit.SECONDS);
                redisTemplate.opsForValue().set(tokenKey, sessionJson, ttl, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(sessionKey, sessionJson);
                redisTemplate.opsForValue().set(tokenKey, sessionJson);
            }

            // 添加到用户会话索引
            redisTemplate.opsForSet().add(USER_SESSION_PREFIX + session.getUsername(), session.getId());
        } catch (Exception e) {
            log.error("Failed to save session: {}", session.getId(), e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    @Override
    public void update(SessionEntity session) {
        try {
            String sessionKey = SESSION_PREFIX + session.getId();
            String tokenKey = TOKEN_PREFIX + session.getToken();
            String sessionJson = objectMapper.writeValueAsString(session);

            // expireTime 是 yyyyMMddHHmmss 格式的 Long，需转换回真实时间戳计算 TTL
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            long expireMillis = sdf.parse(String.valueOf(session.getExpireTime())).getTime();
            long ttl = (expireMillis - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                redisTemplate.opsForValue().set(sessionKey, sessionJson, ttl, TimeUnit.SECONDS);
                redisTemplate.opsForValue().set(tokenKey, sessionJson, ttl, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(sessionKey, sessionJson);
                redisTemplate.opsForValue().set(tokenKey, sessionJson);
            }
        } catch (Exception e) {
            log.error("Failed to update session: {}", session.getId(), e);
            throw new RuntimeException("Failed to update session", e);
        }
    }

    @Override
    public void deleteById(String id) {
        SessionEntity session = findById(id);
        if (session != null) {
            String sessionKey = SESSION_PREFIX + id;
            String tokenKey = TOKEN_PREFIX + session.getToken();
            redisTemplate.delete(sessionKey);
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(USER_SESSION_PREFIX + session.getUsername(), id);
        }
    }

    @Override
    public void deleteByUsername(String username) {
        List<SessionEntity> sessions = findByUsername(username);
        for (SessionEntity session : sessions) {
            deleteById(session.getId());
        }
    }

    @Override
    public void cleanExpired() {
        // Redis 会自动清理过期数据，这里可以手动扫描清理
        log.info("RedisTokenStore cleanExpired - Redis handles expiration automatically");
    }
}