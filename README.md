# Open Ground

轻量级 Java 后端基础工具包 — 统一响应、加解密工具、操作日志框架、对象存储、序列号生成。

[![Maven Central](https://img.shields.io/maven-central/v/io.github.open-ground/open-ground)](https://central.sonatype.com/artifact/io.github.open-ground/open-ground)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![JDK](https://img.shields.io/badge/JDK-17+-green)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green)

---

## 模块概览

```
open-ground (pom)
├── open-ground-dependency (pom)      — BOM，统一依赖版本管理
├── open-ground-base (jar)            — 核心 DTO、异常、工具类
├── open-ground-common (pom)          — 功能模块聚合
│   ├── open-ground-common-api (jar)           — SPI 接口、注解、枚举、DTO（零 Spring 依赖）
│   ├── open-ground-common-core (jar)          — AOP 切面、请求过滤器、自动配置
│   └── open-ground-common-springcloud (jar)   — Feign 远程 SPI 实现（微服务模式）
├── open-ground-security (pom)        — 认证安全模块聚合
│   ├── open-ground-security-api (jar)         — 认证 SPI 接口定义（零 Spring 依赖）
│   ├── open-ground-security-core (jar)        — Token 管理、会话存储、API Key、鉴权过滤器
│   └── open-ground-security-springboot-starter (jar) — 自动配置 Starter
└── open-ground-starter (pom)         — 一键引入 BOM，聚合全部依赖
```

### open-ground-base

基础模块，无 Spring Boot 自动配置依赖，包含：

| 类别 | 内容 |
|------|------|
| **DTO** | `CommonResult<T>`（统一响应）、`PaginatedResult<T>`（分页响应）、`Tree<T>`（树节点模型）、`Error`（错误信息） |
| **异常** | `CommonException`（链式调用，含错误码） |
| **常量** | `ErrorCode`（错误码定义：0000 成功、0400-0499 认证授权、1000-1006 系统错误、9999 校验失败）、`PageConstant`（默认分页参数） |
| **加密工具** | `AESUtil`（AES/CBC/PKCS5Padding）、`RSAUtil`（RSA/ECB/PKCS1Padding 2048位）、`SM4Utils`（国密 SM4/CBC/PKCS5）、`SignUtil`（签名验签） |
| **工具类** | `DateUtils`（日期计算与范围生成）、`IpUtils`（客户端 IP 提取）、`JsonUtil`（Fastjson 封装）、`MapUtil`（Map 安全取值）、`AmtUtil`（金额计算）、`ListUtils`（列表排序聚合）、`StringUtils`（字符串处理）、`FileUtil`（文件上传）、`PageUtil`（分页计算）、`SerializeUtil`（序列化）、`SpringUtil`（静态 Bean 获取）、`RequestUtil`（请求上下文 ThreadLocal）、`FormatHashMap`（点号键 HashMap）、`CommonUtil`（通用工具） |

### open-ground-common-api

SPI 接口定义层，**零 Spring 依赖**，可被非 Spring 项目引用：

- **操作日志 SPI** — `@OptLog` 注解、`LogSender` 接口、`SysOptLog` DTO、`OptType`/`OptStatus` 枚举、`OptLogEvent` 事件
- **序列号 SPI** — `SequenceProvider` 接口、`IdGenerator`（53 位雪花算法）、`KeyInfoDomain` 序列元数据
- **条件注解** — `@ConditionalOnAuth`（集成模式）、`@ConditionalOnService`（微服务模式）

### open-ground-common-core

Spring Boot 自动配置模块，提供 SPI 接口的默认实现：

- **请求过滤器** — `CommonRequestFilter`：Token 校验、AES/SM4 解密、签名验签、SQL 注入拦截、URL 非法字符检查。通过 `ground.security.request-filter.*` 配置
- **操作日志** — `OptLogAspect` AOP 切面 + `JdbcLogSender`（JDBC 持久化），通过 `@EnableOptLog` 启用
- **序列号生成** — `JdbcSequenceProvider`（基于数据库表 `SYS_AUTO_PMKEY`）、`KeyGenerator`（缓存式批量生成）、`Snowflake`（16 位雪花算法）
- **对象存储** — `OssClient` 接口 + `S3OssClient`（AWS S3 SDK 实现），通过 `ground.oss.*` 配置

### open-ground-common-springcloud

微服务模式下的 Feign 远程实现，通过 `ground.mode=service` 激活：

- `FeignTokenCheckService` — 远程调用认证服务 Token 校验
- `FeignSequenceProvider` — 远程获取序列号
- `FeignLogSender` — 远程保存操作日志

### open-ground-starter

一键引入 BOM，聚合以下依赖：

- Open Ground 自身模块（base + common-core）
- Spring Boot：web（Undertow）、aop、data-redis、configuration-processor
- Spring Cloud：loadbalancer
- 数据库：MyBatis、Druid、MySQL、PageHelper
- 安全：JJWT、Spring Security Crypto、Jasypt
- API 文档：SpringDoc OpenAPI
- 工具：Lombok、Fastjson、Hutool、Guava、Commons、EasyExcel、Gson、JSqlParser、Dom4j、UserAgentUtils
- 日志：Logback
- 对象存储：AWS S3 SDK
- HTTP Client：HttpClient5

### open-ground-dependency

纯 BOM 模块（无任何依赖声明），仅提供 `<dependencyManagement>` 供其他项目 `import` 使用，确保版本一致。

### open-ground-security

认证安全模块，提供完整的用户认证与会话管理能力：

| 子模块 | 说明 |
|--------|------|
| `open-ground-security-api` | SPI 接口定义层：`UserDetails`（用户详情）、`UserDetailsService`（用户查询服务）、`TokenStore`（会话存储接口）、`ApiKeyService`（API Key 服务）、`TokenManager`（Token 管理器）、`SecurityContextHolder`（安全上下文） |
| `open-ground-security-core` | 核心实现：`DbTokenStore`（数据库会话存储）、`RedisTokenStore`（Redis 会话存储）、`ApiKeyServiceImpl`（API Key 实现）、`AuthTokenManagerFilter`（Token 鉴权过滤器）、`TokenExtractor`（Token 提取 SPI）、`SessionMapper`/`ApiKeyMapper`（MyBatis 映射） |
| `open-ground-security-springboot-starter` | Spring Boot 自动配置，通过 `@EnableConfigurationProperties` 加载 `AuthProperties` 和 `TokenFilterProperties` |

**核心能力：**

- **用户认证** — 基于 `UserDetailsService` SPI 的用户认证机制，支持用户名、手机号、用户ID 等多种查询方式
- **Token 管理** — 基于 `TokenManager` 的 Token 生成、验证（含滑动过期）、刷新、销毁，支持多设备登录控制
- **会话存储** — 支持数据库（`DbTokenStore`，默认）和 Redis（`RedisTokenStore`）两种存储方式，通过 `ground.security.token-store` 切换
- **API Key 管理** — `sk-` 前缀的 API Key 生成、验证、撤销，支持过期时间和启用/禁用控制
- **鉴权过滤器** — `AuthTokenManagerFilter` 自动拦截请求，提取 Token 并校验，支持白名单路径放行和自定义 `TokenExtractor`
- **安全上下文** — `SecurityContextHolder` 基于 ThreadLocal 的当前用户上下文管理，请求结束后自动清除

---

## 架构设计

项目采用 **SPI + 双部署模式** 架构：

```
                  open-ground-starter (聚合 BOM)
                         │
          ┌──────────────┼──────────────┬──────────────────┐
          │              │              │                  │
    open-ground-base  open-ground-common  open-ground-dependency  open-ground-security
          │              │                      (BOM)              │
          │     ┌────────┼────────┐                    ┌───────────┼───────────┐
          │     │        │        │                    │           │           │
          │  common-api  core  springcloud          security-api  core    starter
          │  (SPI 定义)  (实现)  (Feign 实现)        (SPI 定义)  (实现)  (自动配置)
          │
     DTO/工具类    注解/枚举    AOP/过滤器    远程 Feign 调用    UserDetails    TokenStore
                    SPI 接口    自动配置       (token/keygen/log) TokenManager  AuthFilter
                               JDBC 实现                        ApiKeyService  SecurityContext
```

### 两种部署模式

通过 `ground.mode` 配置切换：

| 模式 | 值 | 说明 |
|------|----|------|
| **集成模式** | `auth`（默认） | 直接通过 JDBC 操作数据库，适用于单体应用 |
| **微服务模式** | `service` | 通过 Feign 远程调用认证服务，适用于微服务架构 |

---

## 快速开始

### 引入依赖

```xml
<!-- 一键引入（推荐） -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-starter</artifactId>
    <version>${open-ground.version}</version>
    <type>pom</type>
</dependency>

<!-- 或按需引入 -->
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-base</artifactId>
    <version>${open-ground.version}</version>
</dependency>
```

### 统一响应

```java
// 成功
return CommonResult.success(data);
return CommonResult.success("操作成功", data);

// 分页
return PaginatedResult.success(list, total, page, perPage);

// 失败（通过异常）
throw new CommonException().setCode(ErrorCode.USER_NOT_FOUND_ERROR).setMsg("用户不存在");
```

### 启用操作日志

```java
@SpringBootApplication
@EnableOptLog  // 启用操作日志 AOP
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```java
@RestController
public class UserController {

    @OptLog(value = "创建用户", type = OptType.INSERT)
    @PostMapping("/user")
    public CommonResult<User> create(@RequestBody UserDTO dto) {
        // ...
    }
}
```

### 启用请求过滤器

```yaml
ground:
  mode: auth   # 集成模式（默认）
  security:
    request-filter:
      enabled: true               # 启用过滤器
      token-check-enabled: true   # 启用 Token 校验
      decrypt-enabled: true       # 启用请求解密
      algorithm: AES              # 解密算法：AES / SM4
      encrypt-key: ${ENCRYPT_KEY}
      encrypt-iv: ${ENCRYPT_IV}
      sign-expire: 300            # 签名过期时间（秒）
```

实现 `TokenCheckService` SPI 接口并注册为 Bean 即可自定义 Token 校验逻辑：

```java
@Component
public class MyTokenCheckService implements TokenCheckService {
    @Override
    public void check(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new CommonException().setCode("0401").setMsg("无效 Token");
        }
        // 自定义校验逻辑...
    }
}
```

### 使用序列号生成器

```java
@Component
public class MyService {
    @Autowired
    private KeyGenerator keyGenerator;

    public void createOrder() {
        // 格式化序列：前缀 + 日期 + 流水号，如 ORD202501010001
        String orderNo = keyGenerator.businessKey("ORD", "ORDER_SEQ");
        // 雪花算法 ID
        Long id = keyGenerator.internalKey();
        // 纯数字序列
        Long nextKey = keyGenerator.nextKey("ORDER_SEQ");
    }
}
```

### 使用对象存储

```yaml
ground:
  oss:
    enable: true
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    region: cn-hangzhou
    path-style-access: false
    domain: https://your-bucket.oss-cn-hangzhou.aliyuncs.com
    access-key: ${OSS_ACCESS_KEY}
    access-secret: ${OSS_ACCESS_SECRET}
    bucket-name: your-bucket
```

```java
@Component
public class MyService {
    @Autowired
    private OssClient ossClient;

    public void upload(byte[] data, String key) {
        ossClient.putObject(data, key);
        String url = ossClient.getObjectURL(key, 3600); // 1小时有效期
    }
}
```

### 加密工具使用

```java
// AES 加解密
String encrypted = AESUtil.encrypt("Hello", key, iv);
String decrypted = AESUtil.decrypt(encrypted, key, iv);

// RSA 加解密
KeyPair keyPair = RSAUtil.getKeys();
String encrypted = RSAUtil.encrypt("Hello", keyPair.getPublic());
String decrypted = RSAUtil.decrypt(encrypted, keyPair.getPrivate());

// 国密 SM4 加解密
String encrypted = SM4Utils.encrypt("Hello", key, iv);
String decrypted = SM4Utils.decrypt(encrypted, key, iv);
```

### 使用认证安全模块

引入依赖：

```xml
<dependency>
    <groupId>io.github.open-ground</groupId>
    <artifactId>open-ground-security-springboot-starter</artifactId>
    <version>${open-ground.version}</version>
</dependency>
```

#### 实现 UserDetailsService

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        return DefaultUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .realName(user.getRealName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .roleIds(user.getRoleIds())
                .status(user.getStatus())
                .enabled(user.getStatus() == 1)
                .accountNonLocked(user.getStatus() == 1)
                .createTime(user.getCreateTime())
                .build();
    }
}
```

#### 用户登录（生成 Token）

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private TokenManager tokenManager;
    @Autowired
    private UserDetailsService userDetailsService;

    @PostMapping("/login")
    public CommonResult<String> login(@RequestParam String username,
                                      @RequestParam String password,
                                      HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        // 验证密码（业务自行实现）
        // ...

        // 设置客户端 IP
        DefaultUserDetails details = (DefaultUserDetails) userDetails;
        details.setClientIp(IpUtils.getIpAddr(request));

        // 生成 Token
        Map<String, String> params = new HashMap<>();
        String token = tokenManager.generateToken(details, "password", params);
        return CommonResult.success(token);
    }
}
```

#### 获取当前用户

```java
@GetMapping("/user/info")
public CommonResult<UserInfo> getUserInfo() {
    UserDetails currentUser = SecurityContextHolder.getCurrentUser();
    if (currentUser == null) {
        throw new CommonException(ErrorCode.NO_LOGIN, "未登录");
    }
    // 转换为业务 DTO...
    return CommonResult.success(userInfo);
}
```

#### 配置 Token 鉴权过滤器

```yaml
ground:
  security:
    token-store: db                      # 会话存储方式：db（默认）/ redis
    token-timeout: 1440                  # Token 超时时间（分钟），默认 1440（24小时）
    multi-login: true                    # 是否允许多设备登录
    token-filter:
      enabled: true                      # 启用 Token 鉴权过滤器
      white-list:                        # 白名单路径（无需 Token 校验）
        - /auth/login
        - /auth/register
        - /swagger-ui/**
        - /v3/api-docs/**
      order: -100                        # 过滤器顺序

---

## 配置参考

### 完整配置项

```yaml
ground:
  mode: auth                                    # 部署模式：auth（集成）/ service（微服务）

  security:
    request-filter:
      enabled: false                            # 是否启用请求过滤器
      order: -100                               # 过滤器顺序
      url-regular-enabled: true                 # 是否启用 URL 正则校验
      decrypt-enabled: false                    # 是否启用请求解密
      algorithm: AES                            # 解密算法：AES / SM4
      encrypt-key: ''                           # 加密密钥
      encrypt-iv: ''                            # 加密向量
      sign-expire: 300                          # 签名过期时间（秒）
      token-check-enabled: false                # 是否启用 Token 校验

  log:
    enabled: true                               # 是否启用操作日志
    log-length: 2000                            # 日志参数截断长度

  security:
    token-store: db                             # 会话存储方式：db / redis
    token-timeout: 1440                         # Token 超时时间（分钟）
    multi-login: true                           # 是否允许多设备登录
    token-filter:
      enabled: true                             # 是否启用 Token 鉴权过滤器
      order: -100                               # 过滤器顺序
      white-list:                               # 白名单路径（Ant 路径模式）
        - /swagger-ui/**
        - /v3/api-docs/**
        - /error
        - /favicon.ico
      url-patterns:                             # 拦截路径
        - /*
    api-key:
      enabled: true                             # 是否启用 API Key
      expire-days: 30                           # API Key 默认过期天数

  oss:
    enable: false                               # 是否启用对象存储
    endpoint: ''                                # OSS 端点
    region: ''                                  # 区域
    path-style-access: false                    # 是否使用路径风格
    domain: ''                                  # 自定义域名
    access-key: ''                              # 访问密钥
    access-secret: ''                           # 访问密钥密码
    bucket-name: ''                             # 默认桶名
    max-connections: 100                        # 最大连接数
```

---

## 要求

- JDK 17+
- Spring Boot 3.x
- Maven 3.6+

## 许可

Apache License 2.0

Copyright 2025 Open Ground
