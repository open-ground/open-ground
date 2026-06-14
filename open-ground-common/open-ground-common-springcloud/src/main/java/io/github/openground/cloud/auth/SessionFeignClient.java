package io.github.openground.cloud.auth;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.security.SessionEntity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理 Feign 客户端
 * <p>用于分离部署模式（{@code ground.mode=service}），远程调用 Auth 服务的会话管理接口。</p>
 * <p>与 {@link AuthFeignClient} 使用相同的路由配置，指向同一个 Auth 服务。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@FeignClient(name = "${ground.auth.application.name:ground-auth}",
             path = "${ground.auth.server.context-path:}",
             url = "${ground.auth.url:}")
public interface SessionFeignClient {

    /**
     * 根据 Token 查找会话
     */
    @GetMapping("/funs/session/findByToken")
    CommonResult<SessionEntity> findByToken(@RequestParam("token") String token);

    /**
     * 根据 ID 查找会话
     */
    @GetMapping("/funs/session/findById")
    CommonResult<SessionEntity> findById(@RequestParam("id") String id);

    /**
     * 根据用户名查找会话列表
     */
    @GetMapping("/funs/session/findByUsername")
    CommonResult<List<SessionEntity>> findByUsername(@RequestParam("username") String username);

    /**
     * 保存会话
     */
    @PostMapping("/funs/session/save")
    CommonResult<Void> save(@RequestBody SessionEntity session);

    /**
     * 更新会话
     */
    @PostMapping("/funs/session/update")
    CommonResult<Void> update(@RequestBody SessionEntity session);

    /**
     * 根据 ID 删除会话
     */
    @DeleteMapping("/funs/session/deleteById")
    CommonResult<Void> deleteById(@RequestParam("id") String id);

    /**
     * 根据用户名删除所有会话
     */
    @DeleteMapping("/funs/session/deleteByUsername")
    CommonResult<Void> deleteByUsername(@RequestParam("username") String username);

    /**
     * 清理过期会话
     */
    @PostMapping("/funs/session/cleanExpired")
    CommonResult<Void> cleanExpired();
}
