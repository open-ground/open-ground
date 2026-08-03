# 通知消息推送组件 — 详细设计

## 1. 背景与目标

### 1.1 设计动机

在企业级应用中，多系统需要发送通知（审批提醒、流程更新、系统告警等），但各系统自行实现导致：

- 通知渠道分散：站内信、钉钉、企微、邮件、短信各写一套
- 重复代码：异常处理、日志、重试逻辑在各处重复
- 扩展困难：新增一个渠道需要改动多处业务代码

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| **统一入口** | 业务系统只需调用 `Notifier.send()`，无需关心具体渠道 |
| **开箱即用** | Spring Boot 自动配置，引入依赖即可使用 |
| **灵活扩展** | 新增渠道只需实现 `NotifyChannel` 接口 |
| **轻量简洁** | 零外部模板依赖，调用方自行准备内容 |
| **异步支持** | 内置 `@Async` + CompletableFuture 异步发送 |

### 1.3 非目标

- ❌ 不做模板管理/模板存储/模板在线编辑
- ❌ 不做消息队列/事件驱动（可基于异步实现类似效果）
- ❌ 不做消息已读/未读状态管理
- ❌ 不做前端展示层

---

## 2. 整体架构

### 2.1 模块位置

组件位于 open-ground-common 已有的 api / core 子模块中，不创建独立子模块：

| 模块 | 包路径 |
|------|--------|
| `open-ground-common-api` | `io.github.openground.common.notification.*` |
| `open-ground-common-core` | `io.github.openground.common.notification.*` |

### 2.2 分层说明

```
┌──────────────────────────────────────────────────────┐
│                  业务系统 (ground-auth 等)              │
│  Notifier.send(NotifyRequest)                         │
└─────────────────────┬────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────┐
│              NotifierImpl (Core)                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │ 1. 渲染模板 (DefaultNotifyTemplateRenderer)      │  │
│  │ 2. 构建 NotifyMessage                            │  │
│  │ 3. 遍历渠道 → 异常隔离 → 结果聚合                │  │
│  └─────────────────────────────────────────────────┘  │
└──────┬──────────────────────────────────┬─────────────┘
       │                                  │
       ▼                                  ▼
┌──────────────┐              ┌──────────────────────┐
│ NotifyChannel│◄── SPI ────│ 扩展渠道 (自行实现)    │
│ (接口)       │              │ ├── DingTalkChannel   │
├──────────────┤              │ ├── WeComChannel      │
│ Internal     │              │ ├── EmailChannel      │
│ NotifyChannel│              │ └── SmsChannel        │
│ (默认站内信)  │              └──────────────────────┘
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ NotifyMessage│
│ Store (SPI)  │ ◄── 业务方实现 (如 ground-auth)
└──────────────┘
```

### 2.3 核心流程

```
业务系统调用
    │
    ▼
构建 NotifyRequest
    │  receivers, title, content, templateParams(可选), channels(可选)
    ▼
Notifier.send(NotifyRequest)
    │
    ├─ 1. 如有 templateParams → 渲染 content 中的 {{var}} 占位符
    │
    ├─ 2. 构建 NotifyMessage (含 messageId, createTime 等)
    │
    ├─ 3. 确定目标渠道列表
    │   ├── 指定 channels → 过滤出匹配的已启用渠道
    │   └── 未指定 → 全部已启用渠道
    │
    ├─ 4. 按 order 排序，逐渠道 send(NotifyMessage)
    │   ├── 成功 → 记录结果
    │   └── 异常 → 捕获记录，不中断其他渠道 (异常隔离)
    │
    └─ 5. 聚合各渠道结果 → 返回 NotifyResponse
```

---

## 3. API 层设计

### 3.1 Notifier — 业务统一入口

```java
package io.github.openground.common.notification;

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
     * 基于 Spring @Async + CompletableFuture，不阻塞调用线程
     *
     * @param request 通知请求
     * @return 异步结果，可等待或回调
     */
    CompletableFuture<NotifyResponse> sendAsync(NotifyRequest request);
}
```

### 3.2 NotifyChannel — 通知渠道 SPI

```java
package io.github.openground.common.notification;

/**
 * 通知渠道 SPI
 *
 * <p>实现此接口并注册为 Spring Bean，组件自动发现并管理。
 * 每个渠道实现一个消息通道（站内信、钉钉、企微、邮件、短信等）。
 *
 * <p>示例实现：
 * <pre>{@code
 * @Component
 * public class DingTalkChannel implements NotifyChannel {
 *     @Override
 *     public String channelType() { return "DINGTALK"; }
 *
 *     @Override
 *     public ChannelResult send(NotifyMessage message) {
 *         // 将 message 转为钉钉消息格式并发送
 *     }
 * }
 * }</pre>
 */
public interface NotifyChannel {

    /**
     * 渠道类型标识，如 INTERNAL、DINGTALK、WECOM、EMAIL、SMS
     */
    String channelType();

    /**
     * 发送通知
     *
     * @param message 标准化通知消息（已渲染完成）
     * @return 该渠道的发送结果
     */
    ChannelResult send(NotifyMessage message);

    /**
     * 渠道是否可用
     * 可通过配置动态控制
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 优先级，数值越小越先执行
     */
    default int getOrder() {
        return 100;
    }
}
```

### 3.3 NotifyRequest — 业务请求 DTO

```java
package io.github.openground.common.notification;

/**
 * 通知请求：业务系统调用时传入的参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyRequest {

    /** 接收人列表（用户ID / 手机号 / 邮箱，由渠道自行解释） */
    @NotEmpty
    private List<String> receivers;

    /** 通知标题（可含 {{var}} 占位符） */
    @NotBlank
    private String title;

    /** 通知内容（可含 {{var}} 占位符） */
    @NotBlank
    private String content;

    /** 模板参数：渲染 title 和 content 中的 {{var}} 占位符 */
    private Map<String, Object> templateParams;

    /** 指定渠道类型（为空则发送所有已启用的渠道） */
    private Set<String> channels;

    /** 业务类型标识，用于区分不同业务场景 */
    private String businessType;

    /** 业务主键 */
    private String businessKey;

    /** 发送者标识（为空默认 system） */
    private String sender;

    /** 扩展参数，用于传递渠道特有参数 */
    private Map<String, Object> extras;
}
```

### 3.4 NotifyMessage — 标准化消息体

```java
package io.github.openground.common.notification;

/**
 * 标准化通知消息：NotifierImpl 将 NotifyRequest 转换后分发给各渠道
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyMessage {

    /** 消息唯一ID（由 NotifierImpl 生成，用于追踪/去重） */
    private String messageId;

    /** 接收人列表 */
    private List<String> receivers;

    /** 通知标题（已渲染完成） */
    private String title;

    /** 通知内容（已渲染完成） */
    private String content;

    /** 业务类型 */
    private String businessType;

    /** 业务主键 */
    private String businessKey;

    /** 发送者 */
    private String sender;

    /** 扩展参数 */
    private Map<String, Object> extras;

    /** 创建时间 */
    private Date createTime;
}
```

### 3.5 NotifyResponse / ChannelResult — 响应结果

```java
package io.github.openground.common.notification;

/**
 * 通知响应：聚合所有渠道的发送结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyResponse {

    /** 是否全部发送成功 */
    private boolean allSuccess;

    /** 各渠道的发送结果 */
    private Map<String, ChannelResult> channelResults;

    /** 成功渠道数 */
    private int successCount;

    /** 失败渠道数 */
    private int failCount;
}

/**
 * 单个渠道的发送结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResult {

    /** 渠道类型 */
    private String channelType;

    /** 是否发送成功 */
    private boolean success;

    /** 成功时的消息ID（渠道返回的） */
    private String channelMessageId;

    /** 失败时的错误信息 */
    private String errorMessage;
}
```

### 3.6 NotifyMessageStore — 站内信存储 SPI

```java
package io.github.openground.common.notification;

/**
 * 站内信消息存储 SPI
 *
 * <p>InternalNotifyChannel 存储消息时调用此接口。
 * 业务系统实现此接口，将站内信存入自己的消息表。
 *
 * <p>示例实现（ground-auth）：
 * <pre>{@code
 * @Component
 * public class AuthNotifyMessageStore implements NotifyMessageStore {
 *     @Override
 *     public void store(NotifyMessage message, String receiver) {
 *         // 将 message 映射为 InfoDO 并入库
 *     }
 * }
 * }</pre>
 */
public interface NotifyMessageStore {

    /**
     * 存储一条站内信消息
     *
     * @param message  通知消息（含标题、内容、业务信息）
     * @param receiver 接收人标识
     */
    void store(NotifyMessage message, String receiver);
}
```

### 3.7 NotifyTemplateRenderer — 模板渲染 SPI

```java
package io.github.openground.common.notification;

/**
 * 通知模板渲染器 SPI
 *
 * <p>负责将含 {{var}} 占位符的模板字符串渲染为最终文本。
 * 组件提供默认实现（零依赖的字符串替换），业务方可通过 SPI 替换
 * 为自己的模板引擎（如 FreeMarker）。
 */
public interface NotifyTemplateRenderer {

    /**
     * 渲染模板
     *
     * @param template 含占位符的模板字符串，如 "您好，{{userName}}"
     * @param params   参数表
     * @return 渲染后的文本
     */
    String render(String template, Map<String, Object> params);
}
```

### 3.9 SmsSender — 短信发送 SPI

短信发送与站内信、钉钉有显著不同，需要额外处理：

| 特性 | 说明 |
|------|------|
| **模板强制** | 短信必须使用服务商审核过的模板ID + 参数，不能直接发纯文本 |
| **接收人格式** | 手机号（而非用户ID） |
| **厂商差异大** | 阿里云有 SDK、腾讯云有 SDK、华为云需直调 HTTP |
| **内容受限** | 每条 70 字（含签名），超长按多条计费 |

组件提供 `SmsSender` SPI 抽象短信发送，搭配 `SmsChannel`（Core 层实现 NotifyChannel）一起使用：

```java
package io.github.openground.common.notification;

/**
 * 短信发送器 SPI
 *
 * <p>统一短信发送抽象，各服务商（阿里云、腾讯云、华为云等）各自实现。
 * 配合 SmsChannel 使用，SmsChannel 将 NotifyMessage 转为 SmsRequest 后调用此接口。
 *
 * <p>示例实现（阿里云）：
 * <pre>{@code
 * @Component
 * public class AliyunSmsSender implements SmsSender {
 *     @Override
 *     public SmsResult send(SmsRequest request) {
 *         SendSmsRequest req = new SendSmsRequest()
 *             .setPhoneNumbers(String.join(",", request.getPhoneNumbers()))
 *             .setSignName(request.getSignName())
 *             .setTemplateCode(request.getTemplateCode())
 *             .setTemplateParam(request.getTemplateParam());
 *         SendSmsResponse resp = client.sendSms(req);
 *         // 转为 SmsResult
 *     }
 * }
 * }</pre>
 */
public interface SmsSender {

    /**
     * 发送短信
     *
     * @param request 短信发送请求
     * @return 发送结果
     */
    SmsResult send(SmsRequest request);
}

/**
 * 短信发送请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsRequest {

    /** 接收手机号列表 */
    private List<String> phoneNumbers;

    /** 短信签名（如"阿里云"），需在服务商平台审核通过 */
    private String signName;

    /** 模板编码（如"SMS_15305****"），需在服务商平台审核通过 */
    private String templateCode;

    /** 模板参数 JSON 字符串，如 "{\"code\":\"1234\"}" */
    private String templateParam;
}

/**
 * 短信发送结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsResult {

    /** 是否成功 */
    private boolean success;

    /** 服务商返回的消息ID（批量发送时为批次ID） */
    private String bizId;

    /** 失败时的错误信息 */
    private String errorMessage;
}
```

**SmsChannel 的工作方式（Core 层实现）：**

```
SmsChannel.send(NotifyMessage)
    │
    ├─ receivers 解析为手机号列表
    ├─ extras.get("signName") → 短信签名
    ├─ extras.get("templateCode") → 模板ID
    ├─ content 作为模板参数（或 extras.get("templateParam")）
    │
    └─ 构造 SmsRequest → 调用 SmsSender.send()
```

**业务方使用方式：**

```java
notifier.send(NotifyRequest.builder()
    .receivers(List.of("13800138000"))       // 手机号
    .title("验证码")
    .content("{\"code\":\"1234\"}")           // 模板参数 JSON
    .channels(Set.of("SMS"))                 // 仅短信渠道
    .extras(Map.of(
        "signName", "企业名称",
        "templateCode", "SMS_123456"
    ))
    .build());
```

SmsChannel 默认不启用（需要业务方配置 `open-ground.notify.sms.enabled=true` 并提供 SmsSender 实现），避免引入不必要的依赖。

### 3.10 枚举与异常

```java
/**
 * 内置渠道类型常量
 */
public final class NotifyChannelType {
    private NotifyChannelType() {}
    public static final String INTERNAL = "INTERNAL";
    public static final String DINGTALK = "DINGTALK";
    public static final String WECOM    = "WECOM";
    public static final String EMAIL    = "EMAIL";
    public static final String SMS      = "SMS";
}

/**
 * 通知异常
 */
public class NotifyException extends RuntimeException {
    public NotifyException(String message) { super(message); }
    public NotifyException(String message, Throwable cause) { super(message, cause); }
}
```

---

## 4. Core 层设计

### 4.1 NotifierImpl — 核心实现

```java
@Component
@Slf4j
public class NotifierImpl implements Notifier {

    private final List<NotifyChannel> channels;
    private final NotifyTemplateRenderer renderer;

    public NotifierImpl(
            @Autowired(required = false) List<NotifyChannel> channels,
            @Autowired(required = false) NotifyTemplateRenderer renderer) {
        this.channels = channels != null ? channels : new ArrayList<>();
        this.renderer = renderer != null ? renderer : new DefaultNotifyTemplateRenderer();
        init();
    }

    @PostConstruct
    public void init() {
        this.channels.sort(Comparator.comparingInt(NotifyChannel::getOrder));
        // 打印已注册渠道
    }

    @Override
    public NotifyResponse send(NotifyRequest request) {
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

    private NotifyResponse doSend(NotifyMessage message, Set<String> targetChannels) {
        Map<String, ChannelResult> results = new LinkedHashMap<>();
        int success = 0, fail = 0;

        for (NotifyChannel channel : this.channels) {
            if (!channel.isEnabled()) continue;
            if (targetChannels != null && !targetChannels.contains(channel.channelType())) continue;

            try {
                ChannelResult result = channel.send(message);
                results.put(channel.channelType(), result);
                if (result.isSuccess()) success++; else fail++;
            } catch (Exception e) {
                log.error("渠道 {} 发送异常", channel.channelType(), e);
                results.put(channel.channelType(),
                    ChannelResult.builder().channelType(channel.channelType())
                        .success(false).errorMessage(e.getMessage()).build());
                fail++;
            }
        }

        return NotifyResponse.builder()
            .allSuccess(fail == 0)
            .channelResults(results)
            .successCount(success)
            .failCount(fail)
            .build();
    }
}
```

### 4.2 InternalNotifyChannel — 站内信默认实现

```java
@Component
@Slf4j
@ConditionalOnProperty(prefix = "open-ground.notify", name = "internal-enabled", havingValue = "true", matchIfMissing = true)
public class InternalNotifyChannel implements NotifyChannel {

    private final NotifyMessageStore messageStore;

    public InternalNotifyChannel(
            @Autowired(required = false) NotifyMessageStore messageStore) {
        this.messageStore = messageStore;
    }

    @Override
    public String channelType() { return NotifyChannelType.INTERNAL; }

    @Override
    public int getOrder() { return 1; }

    @Override
    public ChannelResult send(NotifyMessage message) {
        if (messageStore == null) {
            log.warn("NotifyMessageStore 未实现，站内信消息将被丢弃");
            return ChannelResult.builder().channelType(channelType())
                .success(false).errorMessage("NotifyMessageStore not configured").build();
        }

        try {
            for (String receiver : message.getReceivers()) {
                messageStore.store(message, receiver);
            }
            return ChannelResult.builder().channelType(channelType())
                .success(true).build();
        } catch (Exception e) {
            log.error("站内信发送失败", e);
            return ChannelResult.builder().channelType(channelType())
                .success(false).errorMessage(e.getMessage()).build();
        }
    }
}
```

### 4.3 DefaultNotifyTemplateRenderer — 默认模板渲染器

```java
/**
 * 默认模板渲染器：{{var}} / {{var:default}} 占位符替换
 * 零外部依赖，纯字符串操作
 */
public class DefaultNotifyTemplateRenderer implements NotifyTemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*(\\w+)\\s*(?::\\s*([^}]*))?\\s*}}");

    @Override
    public String render(String template, Map<String, Object> params) {
        if (template == null) return null;
        if (params == null || params.isEmpty()) return template;

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            Object value = params.get(key);
            String replacement = value != null ? value.toString()
                    : (defaultValue != null ? defaultValue : "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
```

### 4.4 配置属性

```yaml
open-ground:
  notify:
    # 站内信渠道开关
    internal-enabled: true
    # 异步线程池配置
    async:
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 100
```

```java
@ConfigurationProperties(prefix = "open-ground.notify")
@Data
public class NotifyProperties {
    private boolean internalEnabled = true;
    private Async async = new Async();

    @Data
    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;
    }
}
```

### 4.5 自动配置

```java
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
@EnableAsync
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NotifyTemplateRenderer.class)
    public NotifyTemplateRenderer notifyTemplateRenderer() {
        return new DefaultNotifyTemplateRenderer();
    }

    @Bean
    @ConditionalOnMissingBean(Notifier.class)
    public Notifier notifier(List<NotifyChannel> channels,
                             NotifyTemplateRenderer renderer) {
        return new NotifierImpl(channels, renderer);
    }

    @Bean("notifyExecutor")
    public Executor notifyExecutor(NotifyProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
```

---

## 5. 使用示例

### 5.1 纯文本发送

```java
notifier.send(NotifyRequest.builder()
    .receivers(List.of("zhangsan"))
    .title("审批提醒")
    .content("您有一条待审批的流程")
    .build());
```

### 5.2 模板占位符发送

```java
notifier.send(NotifyRequest.builder()
    .receivers(List.of("zhangsan"))
    .title("审批提醒")
    .content("您好，{{userName}}，申请单【{{businessKey}}】需要您审批")
    .templateParams(Map.of(
        "userName", "张三",
        "businessKey", "ABC123"
    ))
    .build());
// 实际发送: "您好，张三，申请单【ABC123】需要您审批"
```

### 5.3 异步发送

```java
CompletableFuture<NotifyResponse> future = notifier.sendAsync(request);
future.thenAccept(response -> {
    log.info("发送完成: 成功{}, 失败{}",
        response.getSuccessCount(), response.getFailCount());
});
```

### 5.4 指定渠道发送

```java
notifier.send(NotifyRequest.builder()
    .receivers(List.of("zhangsan"))
    .title("重要告警")
    .content("服务器 CPU 超过 90%")
    .channels(Set.of("DINGTALK", "SMS"))  // 仅走钉钉和短信
    .build());
```

### 5.5 带扩展参数（如钉钉链接）

```java
notifier.send(NotifyRequest.builder()
    .receivers(List.of("zhangsan"))
    .title("审批提醒")
    .content("您有一条待审批的流程")
    .extras(Map.of(
        "linkUrl", "http://portal.example.com/#/approval?taskId=123"
    ))
    .build());

// DingTalkChannel 实现中通过 message.getExtras().get("linkUrl") 获取
```

---

## 6. 扩展指南

### 6.1 新增通知渠道

```java
@Component
public class DingTalkChannel implements NotifyChannel {

    @Value("${dingtalk.webhook:}")
    private String webhook;

    @Override
    public String channelType() { return "DINGTALK"; }

    @Override
    public int getOrder() { return 10; }

    @Override
    public boolean isEnabled() {
        return StringUtils.isNotBlank(webhook);
    }

    @Override
    public ChannelResult send(NotifyMessage message) {
        // 将 message 转为钉钉 Markdown 格式，调用 webhook
        return ChannelResult.builder().channelType(channelType())
            .success(true).build();
    }
}
```

### 6.2 自定义站内信存储

```java
@Component
public class MyMessageStore implements NotifyMessageStore {
    @Override
    public void store(NotifyMessage message, String receiver) {
        // 存入自己系统的消息表
    }
}
```

### 6.3 自定义模板引擎（如 FreeMarker）

```java
@Component
public class FreeMarkerTemplateRenderer implements NotifyTemplateRenderer {
    @Override
    public String render(String template, Map<String, Object> params) {
        // 使用 FreeMarker 渲染
    }
}
// 组件通过 @ConditionalOnMissingBean 自动使用此实现替换默认
```

---

## 7. 实现计划

### 阶段一：API 层 (open-ground-common-api)
- [x] 设计方案确认
- [ ] 创建通知包 `io.github.openground.common.notification`
- [ ] 定义 `NotifyChannel` 接口
- [ ] 定义 `Notifier` 接口
- [ ] 定义 `NotifyRequest` / `NotifyMessage` / `NotifyResponse` / `ChannelResult` DTO
- [ ] 定义 `NotifyChannelType` 常量类
- [ ] 定义 `NotifyException` 异常
- [ ] 定义 `NotifyMessageStore` SPI
- [ ] 定义 `NotifyTemplateRenderer` SPI

### 阶段二：Core 层 (open-ground-common-core)
- [ ] 实现 `NotifierImpl`
- [ ] 实现 `InternalNotifyChannel`（站内信默认渠道）
- [ ] 实现 `DefaultNotifyTemplateRenderer`（{{var}} 替换）
- [ ] 实现 `NotifyProperties` 配置属性
- [ ] 实现 `NotifyAutoConfiguration`（Spring Boot 自动配置 + 线程池）

### 阶段三：异步与重试
- [ ] 集成 @Async 异步支持
- [ ] 实现重试装饰器（失败自动重试）

### 阶段四：ground-auth 对接改造
- [ ] MultiChannelNotificationHandler → 改用 Notifier
- [ ] InternalMessageNotificationChannel → 改用 NotifyChannel + NotifyMessageStore
- [ ] DingTalk 渠道 → 实现 NotifyChannel 接口
- [ ] 验证编译通过

---

## 8. ground-auth-dmp 补录任务催办方案

### 8.1 当前状态

ground-auth-dmp（数据补录模块）中催办功能已有接口骨架，但**未实现真实逻辑**：

| 位置 | 当前代码 |
|------|---------|
| `DmpTaskController.urge()` | `@PostMapping("/urge")`，调用 `taskService.urge(request)` |
| `DmpTaskService.urge()` | 接口方法声明 |
| `DmpTaskServiceImpl.urge()` | `// 预留接口，暂不实现真实催办逻辑` + 仅打印日志 |

ground-auth-dmp **无任何通知/消息发送能力**，不依赖 ground-auth-flow。

### 8.2 催办业务分析

| 问题 | 答案 |
|------|------|
| **谁催办？** | 任务创建人 (`DmpTaskDO.createBy`)、审核人 (`DmpTaskReviewerDO`) |
| **催谁？** | 未提交的补录人 (`DmpTaskAssigneeDO` 中 `isSubmitted = 0`) |
| **何时催？** | 任务状态为 `in_progress` 时，手动调用催办接口 |
| **催办内容？** | "【任务名称】中的【模板名称】数据补录尚未完成，请尽快处理" |
| **通过什么渠道？** | 站内信（默认），后续可扩展企微/钉钉 |

### 8.3 数据关联关系

```
DmpTaskDO (id, taskName, taskStatus, createBy, ...)
    │
    ├── DmpTaskTemplateDO (taskId, templateId, templateStatus, ...)
    │       └── DmpTaskAssigneeDO (taskId, templateId, assigneeUserId, assigneeUserName, isSubmitted)
    │
    └── DmpTaskReviewerDO (taskId, reviewerUserId, reviewerUserName)
```

催办时需查询：
1. 任务基本信息（`DmpTaskDO`）
2. 该任务下的所有模板（`DmpTaskTemplateDO`）
3. 每个模板中未提交的补录人（`DmpTaskAssigneeDO` + `isSubmitted = 0`）

### 8.4 催办流程

```
用户点击"催办"
    │
    ▼
DmpTaskController.urge(request)
    │  request = {taskId: 123}
    ▼
DmpTaskServiceImpl.urge()
    │
    ├─ 1. 校验任务存在且状态为 in_progress
    │
    ├─ 2. 查询所有未提交的补录人（按模板分组）
    │      SELECT * FROM dmp_task_assignee
    │      WHERE task_id = #{taskId} AND is_submitted = 0 AND del_flag = '0'
    │
    ├─ 3. 查询任务名称、各模板名称
    │
    ├─ 4. 按补录人聚合：一个人如果在多个模板未提交，合并为一条通知
    │
    ├─ 5. 构造 NotifyRequest，调用 Notifier.send()
    │      receivers = [未提交的补录人 userId]
    │      title = "数据补录催办提醒"
    │      content = "您好，任务【{{taskName}}】中的以下模板数据补录尚未完成：{{templateNames}}，请尽快登录系统处理。"
    │      templateParams = {taskName, templateNames}
    │      businessType = "DMP_URGE"
    │      businessKey = taskId
    │
    └─ 6. 记录催办日志（可选）
```

### 8.5 urge 接口入参

当前控制器接收 `Map<String, Object>`，保持兼容：

```json
{
  "taskId": 123
}
```

查询未提交的补录人时，还可根据 `templateIds` 筛选指定模板（可选参数，不传则催办所有未提交的模板）。

### 8.6 Mapper 层新增查询

在 `DmpTaskAssigneeMapper` 中新增：

```java
/**
 * 查询指定任务中未提交的补录人（含关联模板信息）
 */
List<UnsubmittedAssigneeVO> selectUnsubmittedByTaskId(@Param("taskId") Long taskId);
```

对应的 XML：

```xml
<select id="selectUnsubmittedByTaskId" resultType="com.dcits.ground.dmp.dto.task.UnsubmittedAssigneeVO">
    SELECT
        a.assignee_user_id AS userId,
        a.assignee_user_name AS userName,
        a.template_id AS templateId,
        t.template_name AS templateName
    FROM dmp_task_assignee a
    LEFT JOIN dmp_template t ON a.template_id = t.id
    WHERE a.task_id = #{taskId}
      AND (a.is_submitted IS NULL OR a.is_submitted = 0)
      AND a.del_flag = '0'
</select>
```

### 8.7 依赖与集成

ground-auth-dmp-core 的 pom.xml 需要新增对 `open-ground-common-core` 的依赖（已存在），通知组件作为 open-ground-common-core 的一部分自动生效。业务方需在 ground-auth-dmp-adapter 或 core 中实现 `NotifyMessageStore` 接口，将站内信存储到 `InfoDO` 表。

### 8.8 实现步骤

1. **通知组件编码**（阶段一至三）
   - 实现 open-ground-common-api/notification 包下所有接口与 DTO
   - 实现 open-ground-common-core/notification 包下所有实现类
   - 编译验证

2. **DMP 站内信存储适配**
   - 在 ground-auth-dmp-adapter 中实现 `NotifyMessageStore`，将消息写入 InfoDO 表
   - 注册为 Spring Bean

3. **催办逻辑实现**
   - 在 `DmpTaskAssigneeMapper` 中新增 `selectUnsubmittedByTaskId` 查询
   - 创建 `UnsubmittedAssigneeVO` DTO
   - 实现 `DmpTaskServiceImpl.urge()` 真实逻辑
   - 注入 `Notifier`，构造 `NotifyRequest` 发送催办通知

4. **编译验证**
   - `mvn compile -pl ground-auth-dmp/ground-auth-dmp-core -am -q -Dmaven.repo.local=/Users/zhangqinsong/workspace/repository`

---

## 9. ground-auth-flow 替换评估报告

### 9.1 现有体系概览

```
ground-auth-flow（当前）
  ┌─────────────────────────────────────────────┐
  │ TaskMessageHandler / FlowInstanceServiceImpl│
  │   ↓ build NotificationContext               │
  │ MultiChannelNotificationHandler              │
  │   → 遍历 NotificationChannel (旧接口)         │
  │     ├─ InternalMessageNotificationChannel    │
  │     └─ DingTalkNotificationChannel (示例)    │
  └─────────────────────────────────────────────┘

open-ground-common（新组件）
  ┌─────────────────────────────────────────────┐
  │ 业务系统                                     │
  │   ↓ build NotifyRequest                     │
  │ Notifier.send()                              │
  │   → 遍历 NotifyChannel (新接口)              │
  │     ├─ InternalNotifyChannel                │
  │     └─ (业务方扩展)                          │
  └─────────────────────────────────────────────┘
```

### 9.2 两套接口对比

| 维度 | 旧 (flow) | 新 (common) |
|------|-----------|-------------|
| 入口 | `MultiChannelNotificationHandler.sendNotification(ctx)` | `Notifier.send(request)` |
| 消息体 | `NotificationContext`（含 taskId、flowCode、nodeName 等工作流字段） | `NotifyRequest`（通用，flow 数据放 extras） |
| 渠道接口 | `NotificationChannel.send(ctx)` → void | `NotifyChannel.send(msg)` → `ChannelResult` |
| 优先级 | `getPriority()` | `getOrder()` |
| channelType | 字符串，如 `"INTERNAL-INFO"` | 字符串，如 `"INTERNAL"` |
| 站内信存储 | `NotificationMessageService.saveMessage(Map)` | `NotifyMessageStore.store(NotifyMessage, receiver)` |

### 9.3 替换工作量

| 改动项 | 涉及文件 | 工作量 |
|--------|---------|--------|
| TaskMessageHandler 中构建 NotifyRequest | 1 个类，替换 `NotificationContext.builder()` → `NotifyRequest.builder()` | **低** |
| FlowInstanceServiceImpl 中构建 NotifyRequest | 1 个类，同上 | **低** |
| InternalMessageNotificationChannel → 改为 NotifyChannel + NotifyMessageStore | 1 个类，实现两个接口 | **中** |
| 删除 MultiChannelNotificationHandler | 1 个类 | **低** |
| 删除 NotificationChannel（旧接口） | 1 个接口 | **低** |
| 删除 NotificationContext | 1 个 DTO | **低** |
| pom.xml 补充 open-ground-common-core 依赖 | 1 个文件 | **低** |

### 9.4 关键风险

`NotificationContext` 中有工作流特有字段（taskId、instanceId、flowCode、nodeName、formPath、serviceName、notificationTypeEnum），替换后需放入 `extras` 中。下游渠道如果读取了这些字段（如 InternalMessageNotificationChannel 中读取 nodeName 拼链接），需改为从 extras 中取。

### 9.5 评估结论

**可以替换，但收益有限。**

| 角度 | 分析 |
|------|------|
| 代码量减少 | 只减少约 2 个类（`MultiChannelNotificationHandler` 和旧的 `NotificationChannel` 接口），约 150 行 |
| 功能增强 | 获得模板渲染、异步发送能力 |
| 统一治理 | flow 模块不再自己管理渠道，所有通知走统一入口 |
| 迁移成本 | 需改动 4-5 个文件，测试回归流程通知场景 |

**推荐：暂缓替换，等 flow 模块下次有通知相关需求时，随改随换。** 目前两套体系可以共存——新业务用 `Notifier`，flow 模块继续走 `MultiChannelNotificationHandler`，互不干扰。
