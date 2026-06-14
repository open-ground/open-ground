package io.github.openground.common.log.listener;

import io.github.openground.common.log.event.OptLogEvent;
import io.github.openground.common.log.service.LogSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * 操作日志事件监听器
 * <p>异步监听 {@link OptLogEvent}，通过 SPI {@link LogSender} 发送操作日志。</p>
 * <p>若未注入 LogSender Bean（集成部署模式），静默跳过。</p>
 * <p>异步执行，不阻塞业务请求线程。</p>
 *
 * @author open-ground
 * @version 1.0
 */
@Slf4j
public class OptLogEventListener {

    /** 无默认实现，分离部署时由 springcloud/sofa/tsf 模块提供 */
    @Autowired(required = false)
    private LogSender logSender;

    /**
     * 处理操作日志事件
     *
     * @param event 操作日志事件
     */
    @Async
    @EventListener
    public void handleOptLogEvent(OptLogEvent event) {
        if (logSender == null) {
            return;
        }
        try {
            logSender.send(event.getOptLog());
        } catch (Exception e) {
            log.warn("操作日志发送失败", e);
        }
    }
}
