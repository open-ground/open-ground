package io.github.openground.common.notification;

import io.github.openground.common.notification.domain.ChannelResult;
import io.github.openground.common.notification.domain.NotifyMessage;
import io.github.openground.common.notification.domain.NotifyRequest;
import io.github.openground.common.notification.domain.NotifyResponse;
import io.github.openground.common.notification.spi.NotifyChannel;
import io.github.openground.common.notification.spi.NotifyTemplateRenderer;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Date;

/**
 * 通知器默认实现
 *
 * <p>核心职责：
 * <ul>
 *   <li>自动发现所有已注册的 NotifyChannel Bean</li>
 *   <li>按优先级排序执行通知发送</li>
 *   <li>异常隔离：单个渠道发送失败不影响其他渠道</li>
 *   <li>模板渲染：将 NotifyRequest 中的 {{var}} 占位符替换为实际值</li>
 * </ul>
 *
 * @author open-ground
 * @since 1.0.6
 */
@Slf4j
public class NotifierImpl implements Notifier {

    private final List<NotifyChannel> channels;

    private final NotifyTemplateRenderer renderer;

    /** 是否已初始化，防止 @PostConstruct + 构造器双重调用 */
    private volatile boolean initialized = false;

    public NotifierImpl(List<NotifyChannel> channels,
                        NotifyTemplateRenderer renderer) {
        this.channels = channels != null ? channels : new ArrayList<>();
        this.renderer = renderer != null ? renderer : new DefaultNotifyTemplateRenderer();
        init();
    }

    @PostConstruct
    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (channels.isEmpty()) {
            log.warn("未检测到任何通知渠道实现（NotifyChannel），通知功能将不可用");
            return;
        }
        channels.sort(Comparator.comparingInt(NotifyChannel::getOrder));
        log.info("已注册 {} 个通知渠道:", channels.size());
        for (NotifyChannel channel : channels) {
            log.info("  - 渠道: {}, 优先级: {}, 状态: {}",
                    channel.channelType(), channel.getOrder(),
                    channel.isEnabled() ? "启用" : "禁用");
        }
    }

    @Override
    public NotifyResponse send(NotifyRequest request) {
        if (request == null) {
            log.warn("通知请求为空，跳过发送");
            return emptyResponse();
        }
        if (request.getReceivers() == null || request.getReceivers().isEmpty()) {
            log.warn("接收人列表为空，跳过发送");
            return emptyResponse();
        }

        // 1. 渲染模板
        String renderedTitle = renderer.render(request.getTitle(), request.getTemplateParams());
        String renderedContent = renderer.render(request.getContent(), request.getTemplateParams());

        // 2. 构建 NotifyMessage
        NotifyMessage message = buildMessage(request, renderedTitle, renderedContent);

        // 3. 分发渠道
        return doSend(message, request.getChannels());
    }

    @Override
    @Async("notifyExecutor")
    public CompletableFuture<NotifyResponse> sendAsync(NotifyRequest request) {
        NotifyResponse response = send(request);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * 构建 {@link NotifyMessage}，将 {@link NotifyRequest} 转为标准化消息体
     */
    private NotifyMessage buildMessage(NotifyRequest request, String renderedTitle, String renderedContent) {
        return NotifyMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .receivers(request.getReceivers())
                .title(renderedTitle)
                .content(renderedContent)
                .businessType(request.getBusinessType())
                .businessKey(request.getBusinessKey())
                .sender(request.getSender() != null ? request.getSender() : "system")
                .extras(request.getExtras())
                .createTime(new Date())
                .build();
    }

    /**
     * 执行渠道分发，按优先级逐个发送，异常隔离
     */
    private NotifyResponse doSend(NotifyMessage message, Set<String> targetChannels) {
        Map<String, ChannelResult> results = new LinkedHashMap<>();
        int success = 0;
        int fail = 0;

        for (NotifyChannel channel : channels) {
            if (!channel.isEnabled()) {
                log.debug("通知渠道 {} 已禁用，跳过", channel.channelType());
                continue;
            }
            if (targetChannels != null && !targetChannels.isEmpty()
                    && !targetChannels.contains(channel.channelType())) {
                log.debug("通知渠道 {} 不在指定列表中，跳过", channel.channelType());
                continue;
            }

            try {
                log.debug("正在通过渠道 {} 发送通知...", channel.channelType());
                ChannelResult result = channel.send(message);
                results.put(channel.channelType(), result);
                if (result.isSuccess()) {
                    success++;
                    log.debug("渠道 {} 通知发送成功", channel.channelType());
                } else {
                    fail++;
                    log.warn("渠道 {} 通知发送失败: {}", channel.channelType(), result.getErrorMessage());
                }
            } catch (Exception e) {
                fail++;
                log.error("渠道 {} 通知发送异常，但不影响其他渠道", channel.channelType(), e);
                results.put(channel.channelType(),
                        ChannelResult.builder()
                                .channelType(channel.channelType())
                                .success(false)
                                .errorMessage(e.getMessage())
                                .build());
            }
        }

        log.info("多渠道通知发送完成 - 任务ID: {}, 成功: {}, 失败: {}",message.getMessageId(), success, fail);
        return NotifyResponse.builder()
                .allSuccess(fail == 0)
                .channelResults(results)
                .successCount(success)
                .failCount(fail)
                .build();
    }

    private NotifyResponse emptyResponse() {
        return NotifyResponse.builder()
                .allSuccess(false)
                .channelResults(new LinkedHashMap<>())
                .successCount(0)
                .failCount(0)
                .build();
    }
}
