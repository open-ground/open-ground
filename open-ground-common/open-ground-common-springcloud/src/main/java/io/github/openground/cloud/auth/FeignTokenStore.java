package io.github.openground.cloud.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.security.SessionEntity;
import io.github.openground.common.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * {@link TokenStore} 的 Feign 远程调用实现
 * <p>适用于分离部署模式（{@code ground.mode=service}），通过 Feign 远程调用 Auth 服务操作会话数据。</p>
 * <p>本地不存储任何会话数据，所有操作委托给远程 Auth 服务。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class FeignTokenStore implements TokenStore {

    private final SessionFeignClient sessionFeignClient;

    @Override
    public SessionEntity findByToken(String token) {
        try {
            CommonResult<SessionEntity> result = sessionFeignClient.findByToken(token);
            if ("0000".equals(result.getCode()) && result.getData() != null) {
                return JSON.parseObject(JSON.toJSONString(result.getData()), SessionEntity.class);
            }
        } catch (Exception e) {
            log.warn("远程调用 findByToken 失败: token={}", token, e);
        }
        return null;
    }

    @Override
    public SessionEntity findById(String id) {
        try {
            CommonResult<SessionEntity> result = sessionFeignClient.findById(id);
            if ("0000".equals(result.getCode()) && result.getData() != null) {
                return JSON.parseObject(JSON.toJSONString(result.getData()), SessionEntity.class);
            }
        } catch (Exception e) {
            log.warn("远程调用 findById 失败: id={}", id, e);
        }
        return null;
    }

    @Override
    public List<SessionEntity> findByUsername(String username) {
        try {
            CommonResult<List<SessionEntity>> result = sessionFeignClient.findByUsername(username);
            if ("0000".equals(result.getCode()) && result.getData() != null) {
                String json = JSON.toJSONString(result.getData());
                return JSON.parseObject(json, new TypeReference<List<SessionEntity>>() {});
            }
        } catch (Exception e) {
            log.warn("远程调用 findByUsername 失败: username={}", username, e);
        }
        return Collections.emptyList();
    }

    @Override
    public void save(SessionEntity session) {
        try {
            CommonResult<Void> result = sessionFeignClient.save(session);
            if ("0000".equals(result.getCode())) {
                log.debug("远程保存会话成功: id={}", session.getId());
            } else {
                log.warn("远程保存会话返回异常: code={}, msg={}", result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("远程调用 save 失败: id={}", session.getId(), e);
        }
    }

    @Override
    public void update(SessionEntity session) {
        try {
            CommonResult<Void> result = sessionFeignClient.update(session);
            if ("0000".equals(result.getCode())) {
                log.debug("远程更新会话成功: id={}", session.getId());
            } else {
                log.warn("远程更新会话返回异常: code={}, msg={}", result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("远程调用 update 失败: id={}", session.getId(), e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            CommonResult<Void> result = sessionFeignClient.deleteById(id);
            if ("0000".equals(result.getCode())) {
                log.debug("远程删除会话成功: id={}", id);
            } else {
                log.warn("远程删除会话返回异常: code={}, msg={}", result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("远程调用 deleteById 失败: id={}", id, e);
        }
    }

    @Override
    public void deleteByUsername(String username) {
        try {
            CommonResult<Void> result = sessionFeignClient.deleteByUsername(username);
            if ("0000".equals(result.getCode())) {
                log.debug("远程删除用户会话成功: username={}", username);
            } else {
                log.warn("远程删除用户会话返回异常: code={}, msg={}", result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("远程调用 deleteByUsername 失败: username={}", username, e);
        }
    }

    @Override
    public void cleanExpired() {
        try {
            sessionFeignClient.cleanExpired();
            log.debug("远程清理过期会话完成");
        } catch (Exception e) {
            log.warn("远程调用 cleanExpired 失败", e);
        }
    }
}
