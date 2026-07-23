package io.github.openground.common.notification;

import io.github.openground.common.notification.domain.NotifyRequest;
import io.github.openground.common.notification.domain.NotifyResponse;

import java.util.concurrent.CompletableFuture;

/**
 * 通知器：业务系统的统一通知调用入口
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private Notifier notifier;
 *
 * // 简单文本通知
 * notifier.send(NotifyRequest.builder()
 *     .receivers(List.of("zhangsan"))
 *     .title("审批提醒")
 *     .content("您有一条待审批的流程")
 *     .build());
 *
 * // 模板占位符通知
 * notifier.send(NotifyRequest.builder()
 *     .receivers(List.of("zhangsan"))
 *     .title("审批提醒")
 *     .content("您好，{{userName}}，业务【{{businessKey}}】需要您审批")
 *     .templateParams(Map.of("userName", "张三", "businessKey", "ABC123"))
 *     .build());
 *
 * // 异步发送
 * CompletableFuture<NotifyResponse> future = notifier.sendAsync(request);
 * }</pre>
 *
 * @author open-ground
 * @since 1.0.6
 */
public interface Notifier {

    /**
     * 同步发送通知到所有指定（或全部）已启用的通知渠道
     *
     * @param request 通知请求（接收人、标题、内容等）
     * @return 各渠道的发送结果聚合
     */
    NotifyResponse send(NotifyRequest request);

    /**
     * 异步发送通知
     * <p>基于 Spring {@code @Async} + {@link CompletableFuture}，不阻塞调用线程。
     *
     * @param request 通知请求
     * @return 异步结果，可等待或回调
     */
    CompletableFuture<NotifyResponse> sendAsync(NotifyRequest request);
}
