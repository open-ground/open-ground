package io.github.openground.common.log.event;

import io.github.openground.common.log.domain.SysOptLog;

/**
 * 操作日志事件
 * <p>由 {@code OptLogAspect} 切面收集日志数据后发布，
 * 由 {@code OptLogEventListener} 异步监听处理（调用 LogSender 发送）。</p>
 * <p>设计为纯 POJO，不继承 ApplicationEvent，零 Spring 依赖。</p>
 *
 * @author open-ground
 * @version 1.0
 */
public class OptLogEvent {

    /** 操作日志数据 */
    private final SysOptLog optLog;

    public OptLogEvent(SysOptLog optLog) {
        this.optLog = optLog;
    }

    /**
     * 获取操作日志数据
     *
     * @return 操作日志对象
     */
    public SysOptLog getOptLog() {
        return optLog;
    }
}
