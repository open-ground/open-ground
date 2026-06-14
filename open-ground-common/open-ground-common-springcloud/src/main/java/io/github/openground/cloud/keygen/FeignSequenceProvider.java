package io.github.openground.cloud.keygen;

import com.alibaba.fastjson.JSON;
import io.github.openground.base.dto.CommonResult;
import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.common.keygen.KeyInfoDomain;
import io.github.openground.common.keygen.SequenceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link SequenceProvider} 的 Feign 远程调用实现
 * <p>适用于分离部署模式，通过 Feign 远程调用 Auth 服务获取序列值。</p>
 *
 * @author open-ground
 */
@Slf4j
@RequiredArgsConstructor
public class FeignSequenceProvider implements SequenceProvider {

    private final AuthFeignClient feignClient;

    @Override
    public KeyInfoDomain retrieveAndAdvance(String keyName, int count) {
        KeyInfoDomain keyinfo = new KeyInfoDomain();
        keyinfo.setPkName(keyName);

        try {
            CommonResult result = feignClient.retrieveKeyFromDB(keyinfo);
            if ("0000".equals(result.getCode())) {
                return JSON.parseObject(JSON.toJSONString(result.getData()), KeyInfoDomain.class);
            } else {
                log.error("远程调用 Auth 获取序列异常: {}-{}", result.getCode(), result.getMessage());
                throw new RuntimeException("远程调用获取序列异常: " + result.getMessage());
            }
        } catch (Exception e) {
            log.error("远程调用 Auth 获取序列失败: keyName={}", keyName, e);
            throw new RuntimeException("远程调用获取序列异常", e);
        }
    }
}
