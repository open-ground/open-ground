package io.github.openground.cloud.auth;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.common.auth.AuthCommonProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * AuthCommonProvider 的 Feign 远程调用实现（Service 部署模式）
 *
 * <p>通过 {@link AuthFeignClient} 远程调用 ground-auth 服务的对应端点，
 * 适用于 {@code ground.mode=service} 的分离部署模式。</p>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
@RequiredArgsConstructor
public class FeignAuthCommonProvider implements AuthCommonProvider {

    private final AuthFeignClient authFeignClient;

    @Override
    public CommonResult<?> getuserFuncs(String userName) {
        log.debug("Feign 调用获取用户功能权限: {}", userName);
        return authFeignClient.getuserFuncs(userName);
    }

    @Override
    public CommonResult<?> getUserInfoByUserName(String userName) {
        log.debug("Feign 调用获取用户信息: {}", userName);
        return authFeignClient.getUserInfoByUserName(userName);
    }

    @Override
    public CommonResult<?> getUsersByUserNames(List<String> userNames) {
        log.debug("Feign 调用批量获取用户信息: {}", userNames);
        return authFeignClient.getUsersByUserNames(userNames);
    }

    @Override
    public CommonResult<?> getDictByType(String dictType, String appName) {
        log.debug("Feign 调用获取字典信息: dictType={}, appName={}", dictType, appName);
        return authFeignClient.getDictByType(dictType, appName);
    }

    @Override
    public CommonResult<?> listAllOrg() {
        log.debug("Feign 调用获取所有机构信息");
        return authFeignClient.listAllOrg();
    }
}