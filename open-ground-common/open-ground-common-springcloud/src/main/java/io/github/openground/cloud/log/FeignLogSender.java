package io.github.openground.cloud.log;

import io.github.openground.base.dto.CommonResult;
import io.github.openground.cloud.auth.AuthFeignClient;
import io.github.openground.common.log.domain.SysOptLog;
import io.github.openground.common.log.service.LogSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 操作日志发送器
 * <p>通过 Feign 远程调用 Auth 服务保存操作日志。</p>
 * <p>分离部署时，业务模块引入 ground-auth-springcloud 自动装配此实现。</p>
 *
 * @author 
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class FeignLogSender implements LogSender {

    private final AuthFeignClient feignClient;

    @Override
    public void send(SysOptLog optLog) {
        if (optLog == null) {
            log.warn("操作日志为空，跳过发送");
            return;
        }
        try {
            CommonResult result = feignClient.insertOptLog(optLog);
            if ("0000".equals(result.getCode())) {
                log.debug("操作日志远程保存成功: type={}, remark={}", optLog.getOptType(), optLog.getOptRemark());
            } else {
                log.warn("操作日志远程保存返回异常: code={}, message={}", result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("操作日志远程保存调用失败: type={}, remark={}", optLog.getOptType(), optLog.getOptRemark(), e);
        }
    }
}
